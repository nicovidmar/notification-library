package dev.challenge.notifications.api;

import dev.challenge.notifications.domain.*;

/**
 * Synchronous entry point. The default client delegates each send once to the selected sender.
 * An explicitly configured sender may retry. A receipt represents provider acceptance.
 */
public interface NotificationClient {
    /**
     * Sends through the default provider for the exact notification type.
     * @throws NotificationValidationException if the notification is null or invalid
     * @throws NotificationConfigurationException if the channel is not configured
     * @throws NotificationDeliveryException if the provider rejects or cannot confirm acceptance
     */
    SendReceipt send(Notification notification);

    /**
     * Sends through the selected provider without changing the channel default.
     * @throws NotificationValidationException if the notification is null or invalid
     * @throws NotificationConfigurationException if the provider is null or not registered for this type
     * @throws NotificationDeliveryException if the provider rejects or cannot confirm acceptance
     */
    SendReceipt send(Notification notification, ProviderId providerId);
}
