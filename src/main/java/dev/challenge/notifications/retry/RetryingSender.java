package dev.challenge.notifications.retry;

import dev.challenge.notifications.domain.FailureCategory;
import dev.challenge.notifications.domain.Notification;
import dev.challenge.notifications.domain.NotificationConfigurationException;
import dev.challenge.notifications.domain.NotificationDeliveryException;
import dev.challenge.notifications.domain.SendReceipt;
import dev.challenge.notifications.spi.NotificationSender;

import java.util.Objects;

/**
 * Explicit, immediate and bounded retries for UNAVAILABLE delivery failures only.
 * Repeating may duplicate effects: even UNAVAILABLE does not establish safe retry.
 * No delay, threads, scheduling, fallback or idempotency guarantee is provided.
 * Thread-safe only when the wrapped sender is thread-safe; attempt counters are local.
 */
public final class RetryingSender<N extends Notification> implements NotificationSender<N> {
    private final NotificationSender<N> delegate;
    private final int maxAttempts;

    /**
     * Enables retry by composition.
     * @param delegate sender called with the same notification on every attempt
     * @param maxAttempts maximum calls including the initial attempt; must be at least 1
     * @throws NotificationConfigurationException if maxAttempts is less than 1
     * @throws NullPointerException if delegate is null
     */
    public RetryingSender(NotificationSender<N> delegate, int maxAttempts) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        if (maxAttempts < 1) {
            throw new NotificationConfigurationException("INVALID_MAX_ATTEMPTS");
        }
        this.maxAttempts = maxAttempts;
    }

    /** Returns the first accepted result or propagates the last applicable failure unchanged. */
    @Override
    public SendReceipt send(N notification) {
        for (int attempt = 1; ; attempt++) {
            try {
                return delegate.send(notification);
            } catch (NotificationDeliveryException failure) {
                if (failure.category() != FailureCategory.UNAVAILABLE || attempt >= maxAttempts) {
                    throw failure;
                }
            }
        }
    }
}
