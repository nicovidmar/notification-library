package consumer;

import dev.challenge.notifications.api.AsyncNotificationClient;
import dev.challenge.notifications.api.NotificationClient;
import dev.challenge.notifications.domain.*;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class AsyncNotificationClientTest {
    private record Message(String id) implements Notification { }
    private final ProviderId provider = new ProviderId("demo");

    private SendReceipt receipt(String id) {
        return new SendReceipt(provider, Optional.of(id), true);
    }

    private NotificationClient delegate(java.util.function.Function<Notification, SendReceipt> send) {
        return new NotificationClient() {
            public SendReceipt send(Notification notification) { return send.apply(notification); }
            public SendReceipt send(Notification notification, ProviderId selected) {
                return new SendReceipt(selected, Optional.of("explicit"), true);
            }
        };
    }

    private static final class ManualExecutor implements Executor {
        final ArrayDeque<Runnable> tasks = new ArrayDeque<>();
        public void execute(Runnable task) { tasks.addLast(task); }
        void runReverse() { while (!tasks.isEmpty()) tasks.removeLast().run(); }
    }

    @Test
    void schedulesAndDelegatesDefaultAndExplicitProvider() {
        var executor = new ManualExecutor();
        var calls = new AtomicInteger();
        var sync = delegate(n -> { calls.incrementAndGet(); return receipt("default"); });
        try (var client = AsyncNotificationClient.using(sync, executor)) {
            var first = client.sendAsync(new Message("a"));
            var selected = new ProviderId("other");
            var second = client.sendAsync(new Message("b"), selected);
            assertFalse(first.isDone());
            assertEquals(0, calls.get());
            executor.runReverse();
            assertEquals("default", first.join().providerMessageId().orElseThrow());
            assertEquals(selected, second.join().providerId());
            assertEquals(1, calls.get());
        }
    }

    @Test
    void batchPreservesOrderDespiteReverseCompletionAndSnapshotsInputs() {
        var executor = new ManualExecutor();
        try (var client = AsyncNotificationClient.using(
                delegate(n -> receipt(((Message) n).id())), executor)) {
            var input = new java.util.ArrayList<Notification>(List.of(new Message("a"), new Message("b")));
            var future = client.sendBatchAsync(input);
            input.clear();
            assertEquals(2, executor.tasks.size());
            executor.runReverse();
            var result = future.join();
            assertEquals("a", ((BatchResult.Accepted) result.get(0)).receipt().providerMessageId().orElseThrow());
            assertEquals("b", ((BatchResult.Accepted) result.get(1)).receipt().providerMessageId().orElseThrow());
            assertThrows(UnsupportedOperationException.class, result::clear);
        }
    }

    @Test
    void batchIsolatesValidationAndDeliveryFailuresIncludingNullEntries() {
        var validation = new NotificationValidationException("notification", "REQUIRED");
        var delivery = new NotificationDeliveryException(provider, FailureCategory.REJECTED);
        var sync = delegate(n -> {
            if (n == null) throw validation;
            if (((Message) n).id().equals("bad")) throw delivery;
            return receipt("ok");
        });
        try (var client = AsyncNotificationClient.using(sync, Runnable::run)) {
            var result = client.sendBatch(Arrays.asList(new Message("ok"), null, new Message("bad")));
            assertInstanceOf(BatchResult.Accepted.class, result.get(0));
            assertSame(validation, ((BatchResult.Failed) result.get(1)).cause());
            assertSame(delivery, ((BatchResult.Failed) result.get(2)).cause());
            assertSame(delivery, assertThrows(CompletionException.class,
                    () -> client.sendAsync(new Message("bad")).join()).getCause());
        }
    }

    @Test
    void rejectedExecutorCompletesFutureInsteadOfThrowingAtSubmission() {
        var rejection = new RejectedExecutionException("full");
        Executor executor = task -> { throw rejection; };
        try (var client = AsyncNotificationClient.using(delegate(n -> receipt("unused")), executor)) {
            var future = assertDoesNotThrow(() -> client.sendAsync(new Message("a")));
            assertSame(rejection, assertThrows(CompletionException.class, future::join).getCause());
            var results = client.sendBatch(List.of(new Message("a"), new Message("b")));
            assertEquals(2, results.size());
            assertSame(rejection, ((BatchResult.Failed) results.get(0)).cause());
            assertSame(rejection, ((BatchResult.Failed) results.get(1)).cause());
        }
    }

    @Test
    void emptyAndNullListsHaveExplicitBehavior() {
        try (var client = AsyncNotificationClient.using(delegate(n -> receipt("unused")), Runnable::run)) {
            assertEquals(List.of(), client.sendBatch(List.of()));
            assertEquals(List.of(), client.sendBatchAsync(List.of()).join());
            var failure = assertThrows(NotificationValidationException.class, () -> client.sendBatchAsync(null));
            assertEquals("notifications", failure.field());
        }
    }

    @Test
    void closeDoesNotShutDownExternalExecutorAndRejectsFurtherCalls() throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            var client = AsyncNotificationClient.using(delegate(n -> receipt("ok")), executor);
            assertTrue(client.sendAsync(new Message("a")).get(10, TimeUnit.SECONDS).simulated());
            client.close();
            client.close();
            assertFalse(executor.isShutdown());
            assertEquals("still usable", executor.submit(() -> "still usable").get(10, TimeUnit.SECONDS));
            assertInstanceOf(RejectedExecutionException.class,
                    assertThrows(CompletionException.class, () -> client.sendAsync(new Message("b")).join()).getCause());
            assertInstanceOf(BatchResult.Failed.class, client.sendBatch(List.of(new Message("b"))).getFirst());
        } finally {
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
        }
    }

    @Test
    void acceptedExternalTasksStillRunAfterFacadeClose() {
        var executor = new ManualExecutor();
        var client = AsyncNotificationClient.using(delegate(n -> receipt("ok")), executor);
        var accepted = client.sendAsync(new Message("a"));
        client.close();
        executor.runReverse();
        assertTrue(accepted.join().simulated());
    }

    @Test
    void aggregateCancellationDoesNotCancelAcceptedChildren() {
        var executor = new ManualExecutor();
        var calls = new AtomicInteger();
        try (var client = AsyncNotificationClient.using(delegate(n -> {
            calls.incrementAndGet();
            return receipt("ok");
        }), executor)) {
            var batch = client.sendBatchAsync(List.of(new Message("a"), new Message("b")));
            assertTrue(batch.cancel(false));
            executor.runReverse();
            assertEquals(2, calls.get());
        }
    }

    @Test
    void defaultExecutorHasBoundedCapacityAndClosesWithoutDiscardingAcceptedTasks() throws Exception {
        var started = new CountDownLatch(4);
        var release = new CountDownLatch(1);
        var threads = ConcurrentHashMap.<Thread>newKeySet();
        var sync = delegate(n -> {
            threads.add(Thread.currentThread());
            started.countDown();
            try {
                if (!release.await(10, TimeUnit.SECONDS)) throw new AssertionError("release timed out");
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new AssertionError(interrupted);
            }
            return receipt("ok");
        });
        var client = AsyncNotificationClient.create(sync);
        var accepted = new java.util.ArrayList<CompletableFuture<SendReceipt>>();
        try {
            for (int i = 0; i < 4; i++) accepted.add(client.sendAsync(new Message("active")));
            assertTrue(started.await(10, TimeUnit.SECONDS));
            for (int i = 0; i < 64; i++) accepted.add(client.sendAsync(new Message("queued")));
            var rejected = client.sendAsync(new Message("overflow"));
            assertInstanceOf(RejectedExecutionException.class,
                    assertThrows(CompletionException.class, rejected::join).getCause());
            client.close();
            assertFalse(accepted.getFirst().isDone(), "close must not cancel running work");
            release.countDown();
            CompletableFuture.allOf(accepted.toArray(CompletableFuture<?>[]::new)).get(10, TimeUnit.SECONDS);
        } finally {
            release.countDown();
            client.close();
            for (Thread thread : threads) {
                thread.join(10000);
                assertFalse(thread.isAlive(), "owned worker must terminate");
            }
        }
    }

    @Test
    void factoryAndOutcomeFieldsCannotBeNull() {
        assertThrows(NullPointerException.class, () -> AsyncNotificationClient.create(null));
        assertThrows(NullPointerException.class,
                () -> AsyncNotificationClient.using(delegate(n -> receipt("ok")), null));
        assertThrows(NullPointerException.class, () -> new BatchResult.Accepted(null));
        assertThrows(NullPointerException.class, () -> new BatchResult.Failed(null));
        assertFalse(new BatchResult.Failed(new RuntimeException("PRIVATE")).toString().contains("PRIVATE"));
    }

    @Test
    void nullReceiptFromABrokenDelegateIsAnIndividualFailure() {
        try (var client = AsyncNotificationClient.using(delegate(n -> null), Runnable::run)) {
            var results = client.sendBatch(List.of(new Message("a"), new Message("b")));
            assertEquals(2, results.size());
            assertInstanceOf(NullPointerException.class, ((BatchResult.Failed) results.get(0)).cause());
            assertInstanceOf(BatchResult.Failed.class, results.get(1));
        }
    }

    @Test
    void batchKeepsSuccessesWhenOnlyOneSubmissionIsRejected() {
        var calls = new AtomicInteger();
        Executor executor = task -> {
            if (calls.incrementAndGet() == 2) throw new RejectedExecutionException("second rejected");
            task.run();
        };
        try (var client = AsyncNotificationClient.using(delegate(n -> receipt("ok")), executor)) {
            var result = client.sendBatch(List.of(new Message("a"), new Message("b"), new Message("c")));
            assertInstanceOf(BatchResult.Accepted.class, result.get(0));
            assertInstanceOf(RejectedExecutionException.class, ((BatchResult.Failed) result.get(1)).cause());
            assertInstanceOf(BatchResult.Accepted.class, result.get(2));
        }
    }

    @Test
    void asyncBatchRunsOnMoreThanOneWorker() throws Exception {
        var started = new CountDownLatch(2);
        var release = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try (var client = AsyncNotificationClient.using(delegate(n -> {
            started.countDown();
            try {
                if (!release.await(10, TimeUnit.SECONDS)) throw new AssertionError("release timed out");
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new AssertionError(interrupted);
            }
            return receipt(((Message) n).id());
        }), executor)) {
            var result = client.sendBatchAsync(List.of(new Message("a"), new Message("b")));
            assertTrue(started.await(10, TimeUnit.SECONDS));
            assertFalse(result.isDone());
            release.countDown();
            assertEquals(2, result.get(10, TimeUnit.SECONDS).size());
        } finally {
            release.countDown();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
        }
    }
}
