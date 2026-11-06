package dev.challenge.notifications.spi;

import dev.challenge.notifications.domain.Notification;
import dev.challenge.notifications.domain.SendReceipt;

/**
 * Provider strategy for one notification type.
 * Return a non-null acceptance receipt with the registered provider ID, or throw a typed
 * notification exception. Validate provider responses, do not expose secrets in failures,
 * and do not silently retry. Document thread safety for each implementation.
 */
@FunctionalInterface
public interface NotificationSender<N extends Notification> {
    SendReceipt send(N notification);
}
