package dev.challenge.notifications.api;

import dev.challenge.notifications.domain.Notification;
import dev.challenge.notifications.domain.NotificationDeliveryException;
import dev.challenge.notifications.domain.NotificationEvent;
import dev.challenge.notifications.domain.ProviderId;
import dev.challenge.notifications.domain.SendReceipt;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Best-effort, synchronous observation of the final client result.
 * The observer runs on the sending thread and must be fast and thread-safe for concurrent use.
 * Observer RuntimeExceptions are discarded; Errors are not caught. No resources are created
 * or owned by this decorator, and no events are persisted or retried.
 */
public final class ObservedNotificationClient implements NotificationClient {
    private final NotificationClient delegate;
    private final Consumer<NotificationEvent> observer;

    /** Wraps a client with one local observer; both references are required. */
    public ObservedNotificationClient(NotificationClient delegate, Consumer<NotificationEvent> observer) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.observer = Objects.requireNonNull(observer, "observer");
    }

    @Override
    public SendReceipt send(Notification notification) {
        return observe(() -> delegate.send(notification));
    }

    @Override
    public SendReceipt send(Notification notification, ProviderId providerId) {
        return observe(() -> delegate.send(notification, providerId));
    }

    private SendReceipt observe(Supplier<SendReceipt> attempt) {
        final SendReceipt receipt;
        try {
            receipt = Objects.requireNonNull(attempt.get(), "receipt");
        } catch (NotificationDeliveryException failure) {
            publish(new NotificationEvent.Failed(failure.providerId(), failure.category()));
            throw failure;
        }
        publish(new NotificationEvent.Accepted(receipt.providerId(), receipt.simulated()));
        return receipt;
    }

    private void publish(NotificationEvent event) {
        try {
            observer.accept(event);
        } catch (RuntimeException ignored) {
            // Best effort: observation must not replace the send result or cause retries.
        }
    }
}
