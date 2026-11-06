package consumer;

import dev.challenge.notifications.api.AsyncNotificationClient;
import dev.challenge.notifications.api.ObservedNotificationClient;
import dev.challenge.notifications.configuration.Notifications;
import dev.challenge.notifications.domain.*;
import dev.challenge.notifications.retry.RetryingSender;
import dev.challenge.notifications.spi.NotificationSender;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class RetryingSenderTest {
    private record Message(String text) implements Notification { }
    private final Message message = new Message("hello");
    private final ProviderId id = new ProviderId("demo");
    private final SendReceipt receipt = new SendReceipt(id, Optional.empty(), true);

    @Test
    void immediateSuccessReturnsTheSameReceiptWithoutRepeating() {
        var calls = new AtomicInteger();
        var sender = new RetryingSender<Message>(n -> {
            assertSame(message, n);
            calls.incrementAndGet();
            return receipt;
        }, 3);
        assertSame(receipt, sender.send(message));
        assertEquals(1, calls.get());
    }

    @Test
    void retriesUnavailableAndStopsOnSuccessWithTheSameInput() {
        var calls = new AtomicInteger();
        var sender = new RetryingSender<Message>(n -> {
            assertSame(message, n);
            if (calls.incrementAndGet() < 3) {
                throw new NotificationDeliveryException(id, FailureCategory.UNAVAILABLE);
            }
            return receipt;
        }, 5);
        assertSame(receipt, sender.send(message));
        assertEquals(3, calls.get());
    }

    @Test
    void exhaustionPropagatesTheLastFailureWithoutAccumulatingCauses() {
        var calls = new AtomicInteger();
        var failures = List.of(
                new NotificationDeliveryException(id, FailureCategory.UNAVAILABLE),
                new NotificationDeliveryException(id, FailureCategory.UNAVAILABLE),
                new NotificationDeliveryException(id, FailureCategory.UNAVAILABLE));
        var sender = new RetryingSender<Message>(n -> { throw failures.get(calls.getAndIncrement()); }, 3);
        assertSame(failures.getLast(),
                assertThrows(NotificationDeliveryException.class, () -> sender.send(message)));
        assertEquals(3, calls.get());
        assertEquals(0, failures.getLast().getSuppressed().length);
    }

    @Test
    void oneAttemptMeansNoRetry() {
        var calls = new AtomicInteger();
        var failure = new NotificationDeliveryException(id, FailureCategory.UNAVAILABLE);
        var sender = new RetryingSender<Message>(n -> { calls.incrementAndGet(); throw failure; }, 1);
        assertSame(failure, assertThrows(NotificationDeliveryException.class, () -> sender.send(message)));
        assertEquals(1, calls.get());
    }

    @ParameterizedTest
    @EnumSource(value = FailureCategory.class, names = "UNAVAILABLE", mode = EnumSource.Mode.EXCLUDE)
    void excludedDeliveryCategoriesAreNeverRetried(FailureCategory category) {
        var calls = new AtomicInteger();
        var failure = new NotificationDeliveryException(id, category);
        var sender = new RetryingSender<Message>(n -> { calls.incrementAndGet(); throw failure; }, 3);
        assertSame(failure, assertThrows(NotificationDeliveryException.class, () -> sender.send(message)));
        assertEquals(1, calls.get());
    }

    @Test
    void stopsWhenARetryProducesAnExcludedFailure() {
        var calls = new AtomicInteger();
        var unavailable = new NotificationDeliveryException(id, FailureCategory.UNAVAILABLE);
        var uncertain = new NotificationDeliveryException(id, FailureCategory.UNKNOWN_OUTCOME);
        var sender = new RetryingSender<Message>(n -> {
            throw calls.incrementAndGet() == 1 ? unavailable : uncertain;
        }, 4);
        assertSame(uncertain, assertThrows(NotificationDeliveryException.class, () -> sender.send(message)));
        assertEquals(2, calls.get());
    }

    @Test
    void validationConfigurationAndProgrammingFailuresPropagateOnce() {
        for (RuntimeException failure : List.of(
                new NotificationValidationException("notification", "REQUIRED"),
                new NotificationConfigurationException("INVALID_CONFIG"),
                new IllegalStateException("bug"))) {
            var calls = new AtomicInteger();
            var sender = new RetryingSender<Message>(n -> { calls.incrementAndGet(); throw failure; }, 3);
            assertSame(failure, assertThrows(RuntimeException.class, () -> sender.send(message)));
            assertEquals(1, calls.get());
        }
        var fatal = new AssertionError("fatal");
        var calls = new AtomicInteger();
        var sender = new RetryingSender<Message>(n -> { calls.incrementAndGet(); throw fatal; }, 3);
        assertSame(fatal, assertThrows(AssertionError.class, () -> sender.send(message)));
        assertEquals(1, calls.get());
    }

    @Test
    void validatesConfigurationBeforeAnyAttempt() {
        var calls = new AtomicInteger();
        NotificationSender<Message> delegate = n -> { calls.incrementAndGet(); return receipt; };
        for (int attempts : new int[] {0, -1}) {
            var failure = assertThrows(NotificationConfigurationException.class,
                    () -> new RetryingSender<>(delegate, attempts));
            assertEquals("INVALID_MAX_ATTEMPTS", failure.code());
        }
        assertThrows(NullPointerException.class, () -> new RetryingSender<Message>(null, 2));
        assertEquals(0, calls.get());
    }

    @Test
    void composesWithAsyncAndObserverAsOneTaskAndOneFinalEvent() throws Exception {
        var calls = new AtomicInteger();
        var threads = ConcurrentHashMap.<Thread>newKeySet();
        var sync = Notifications.builder()
                .register(Message.class, id, new RetryingSender<Message>(n -> {
                    threads.add(Thread.currentThread());
                    if (calls.incrementAndGet() == 1) {
                        throw new NotificationDeliveryException(id, FailureCategory.UNAVAILABLE);
                    }
                    return receipt;
                }, 2))
                .defaultProvider(Message.class, id).build();
        var events = new CopyOnWriteArrayList<NotificationEvent>();
        var observed = new ObservedNotificationClient(sync, events::add);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        var submitted = new AtomicInteger();
        try (var async = AsyncNotificationClient.using(observed, task -> {
            submitted.incrementAndGet();
            executor.execute(task);
        })) {
            assertSame(receipt, async.sendAsync(message).get(10, TimeUnit.SECONDS));
            assertEquals(2, calls.get());
            assertEquals(1, submitted.get());
            assertEquals(1, threads.size());
            assertFalse(threads.contains(Thread.currentThread()));
            assertEquals(List.of(new NotificationEvent.Accepted(id, true)), events);
        } finally {
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
        }
    }

    @Test
    void batchKeepsFinalFailureAndAcceptanceWithoutRetryingExecutorRejection() {
        var failure = new NotificationDeliveryException(id, FailureCategory.UNAVAILABLE);
        var calls = new AtomicInteger();
        var sync = Notifications.builder()
                .register(Message.class, id, new RetryingSender<Message>(n -> {
                    calls.incrementAndGet();
                    if (n.text().equals("bad")) throw failure;
                    return receipt;
                }, 2))
                .defaultProvider(Message.class, id).build();
        var events = new java.util.ArrayList<NotificationEvent>();
        var observed = new ObservedNotificationClient(sync, events::add);
        try (var async = AsyncNotificationClient.using(observed, Runnable::run)) {
            var result = async.sendBatch(List.of(new Message("bad"), message));
            assertSame(failure, ((BatchResult.Failed) result.get(0)).cause());
            assertInstanceOf(BatchResult.Accepted.class, result.get(1));
            assertEquals(3, calls.get());
            assertEquals(List.of(new NotificationEvent.Failed(id, FailureCategory.UNAVAILABLE),
                    new NotificationEvent.Accepted(id, true)), events);
        }

        try (var async = AsyncNotificationClient.using(observed, task -> {
            throw new RejectedExecutionException("full");
        })) {
            assertInstanceOf(RejectedExecutionException.class,
                    assertThrows(CompletionException.class, () -> async.sendAsync(message).join()).getCause());
            assertEquals(3, calls.get());
            assertEquals(2, events.size());
        }
    }
}
