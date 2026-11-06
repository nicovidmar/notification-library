package consumer;

import dev.challenge.notifications.api.AsyncNotificationClient;
import dev.challenge.notifications.api.NotificationClient;
import dev.challenge.notifications.api.ObservedNotificationClient;
import dev.challenge.notifications.configuration.Notifications;
import dev.challenge.notifications.domain.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

class ObservedNotificationClientTest {
    private record Message(String body) implements Notification { }
    private final ProviderId id = new ProviderId("demo");
    private final Message message = new Message("PRIVATE_BODY");
    private final SendReceipt receipt = new SendReceipt(id, Optional.of("PRIVATE_REMOTE_ID"), true);

    private NotificationClient client(java.util.function.Supplier<SendReceipt> attempt) {
        return new NotificationClient() {
            public SendReceipt send(Notification notification) {
                assertSame(message, notification);
                return attempt.get();
            }
            public SendReceipt send(Notification notification, ProviderId provider) {
                assertSame(message, notification);
                assertSame(id, provider);
                return attempt.get();
            }
        };
    }

    @Test
    void observesBothOverloadsAndReturnsTheSameReceipt() {
        var events = new ArrayList<NotificationEvent>();
        var calls = new AtomicInteger();
        var observed = new ObservedNotificationClient(client(() -> {
            calls.incrementAndGet();
            return receipt;
        }), events::add);
        assertSame(receipt, observed.send(message));
        assertSame(receipt, observed.send(message, id));
        assertEquals(2, calls.get());
        assertEquals(List.of(new NotificationEvent.Accepted(id, true),
                new NotificationEvent.Accepted(id, true)), events);
        assertFalse(events.toString().contains("PRIVATE"));
    }

    @ParameterizedTest
    @EnumSource(FailureCategory.class)
    void observesDeliveryFailuresAndPropagatesTheSameException(FailureCategory category) {
        var failure = new NotificationDeliveryException(id, category);
        var events = new ArrayList<NotificationEvent>();
        var observed = new ObservedNotificationClient(client(() -> { throw failure; }), events::add);
        assertSame(failure, assertThrows(NotificationDeliveryException.class, () -> observed.send(message)));
        assertSame(failure, assertThrows(NotificationDeliveryException.class, () -> observed.send(message, id)));
        assertEquals(List.of(new NotificationEvent.Failed(id, category),
                new NotificationEvent.Failed(id, category)), events);
    }

    @Test
    void listenerRuntimeFailureDoesNotTurnAcceptanceIntoDeliveryFailure() {
        var calls = new AtomicInteger();
        var observed = new ObservedNotificationClient(client(() -> receipt), event -> {
            calls.incrementAndGet();
            throw new NotificationDeliveryException(id, FailureCategory.REJECTED);
        });
        assertSame(receipt, observed.send(message));
        assertEquals(1, calls.get(), "listener failure must not create another event");
    }

    @Test
    void listenerRuntimeFailureDoesNotReplaceTheOriginalDeliveryFailure() {
        var original = new NotificationDeliveryException(id, FailureCategory.UNKNOWN_OUTCOME);
        var observed = new ObservedNotificationClient(client(() -> { throw original; }),
                event -> { throw new IllegalStateException("PRIVATE_LISTENER_DATA"); });
        assertSame(original,
                assertThrows(NotificationDeliveryException.class, () -> observed.send(message)));
        assertEquals(0, original.getSuppressed().length);
    }

    @Test
    void validationConfigurationAndBugsDoNotProduceDeliveryEvents() {
        var events = new ArrayList<NotificationEvent>();
        for (RuntimeException original : List.of(
                new NotificationValidationException("notification", "REQUIRED"),
                new NotificationConfigurationException("UNREGISTERED_CHANNEL"),
                new IllegalStateException("programmer error"))) {
            var observed = new ObservedNotificationClient(client(() -> { throw original; }), events::add);
            assertSame(original, assertThrows(RuntimeException.class, () -> observed.send(message)));
        }
        assertEquals(List.of(), events);
    }

    @Test
    void doesNotCatchErrorsFromTheDelegateOrObserver() {
        var fatal = new AssertionError("fatal");
        var calls = new AtomicInteger();
        var failing = new ObservedNotificationClient(client(() -> { throw fatal; }),
                event -> calls.incrementAndGet());
        assertSame(fatal, assertThrows(AssertionError.class, () -> failing.send(message)));
        assertEquals(0, calls.get());

        var observed = new ObservedNotificationClient(client(() -> receipt), event -> { throw fatal; });
        assertSame(fatal, assertThrows(AssertionError.class, () -> observed.send(message)));
    }

    @Test
    void brokenDelegateReturningNullDoesNotAnnounceAcceptance() {
        var events = new ArrayList<NotificationEvent>();
        var observed = new ObservedNotificationClient(client(() -> null), events::add);
        assertThrows(NullPointerException.class, () -> observed.send(message));
        assertEquals(List.of(), events);
    }

    @Test
    void observesClientValidationOfAnInconsistentProviderReceipt() {
        var sync = Notifications.builder()
                .register(Message.class, id, n -> new SendReceipt(
                        new ProviderId("wrong"), Optional.empty(), true))
                .defaultProvider(Message.class, id).build();
        var events = new ArrayList<NotificationEvent>();
        var observed = new ObservedNotificationClient(sync, events::add);
        var failure = assertThrows(NotificationDeliveryException.class, () -> observed.send(message));
        assertEquals(FailureCategory.INVALID_RESPONSE, failure.category());
        assertEquals(List.of(new NotificationEvent.Failed(id, FailureCategory.INVALID_RESPONSE)), events);
    }

    @Test
    void composesWithAsyncAndBatchOnTheSendingWorkerWithoutDuplicateEvents() throws Exception {
        var events = new CopyOnWriteArrayList<NotificationEvent>();
        var callbackThread = new AtomicReference<Thread>();
        Thread caller = Thread.currentThread();
        var observed = new ObservedNotificationClient(client(() -> receipt), event -> {
            events.add(event);
            callbackThread.set(Thread.currentThread());
        });
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try (var async = AsyncNotificationClient.using(observed, executor)) {
            assertSame(receipt, async.sendAsync(message, id).get(10, TimeUnit.SECONDS));
            var result = async.sendBatchAsync(List.of(message, message)).get(10, TimeUnit.SECONDS);
            assertEquals(2, result.size());
            assertEquals(3, events.size());
            assertNotSame(caller, callbackThread.get());
        } finally {
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
        }
    }

    @Test
    void simulationFlagIsCopiedInsteadOfAssumed() {
        var realReceipt = new SendReceipt(id, Optional.empty(), false);
        var event = new AtomicReference<NotificationEvent>();
        var observed = new ObservedNotificationClient(client(() -> realReceipt), event::set);
        assertSame(realReceipt, observed.send(message));
        assertEquals(new NotificationEvent.Accepted(id, false), event.get());
    }

    @Test
    void requiredReferencesCannotBeNull() {
        assertThrows(NullPointerException.class, () -> new ObservedNotificationClient(null, event -> { }));
        assertThrows(NullPointerException.class, () -> new ObservedNotificationClient(client(() -> receipt), null));
        assertThrows(NullPointerException.class, () -> new NotificationEvent.Accepted(null, true));
        assertThrows(NullPointerException.class, () -> new NotificationEvent.Failed(id, null));
        assertThrows(NullPointerException.class,
                () -> new NotificationEvent.Failed(null, FailureCategory.REJECTED));
    }
}
