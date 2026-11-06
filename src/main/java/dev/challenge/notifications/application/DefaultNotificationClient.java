package dev.challenge.notifications.application;

import dev.challenge.notifications.api.NotificationClient;
import dev.challenge.notifications.domain.*;
import dev.challenge.notifications.spi.NotificationSender;

import java.util.HashMap;
import java.util.Map;

/**
 * Immutable routing snapshot. Consumers normally construct it through Notifications.builder().
 * Concurrent calls are supported only when the registered sender implementations are thread-safe.
 */
public final class DefaultNotificationClient implements NotificationClient {
    private final Map<Class<? extends Notification>, Map<ProviderId, NotificationSender<Notification>>> routes;
    private final Map<Class<? extends Notification>, ProviderId> defaults;

    /** Composition entry point; deeply copies the routing maps, not the sender instances. */
    public DefaultNotificationClient(
            Map<Class<? extends Notification>, Map<ProviderId, NotificationSender<Notification>>> routes,
            Map<Class<? extends Notification>, ProviderId> defaults) {
        if (routes == null || defaults == null || routes.isEmpty()
                || !routes.keySet().equals(defaults.keySet())) {
            throw new NotificationConfigurationException("INVALID_DEFAULTS");
        }
        var snapshot = new HashMap<Class<? extends Notification>, Map<ProviderId, NotificationSender<Notification>>>();
        routes.forEach((type, providers) -> {
            if (type == null || providers == null || providers.isEmpty()
                    || providers.entrySet().stream().anyMatch(entry -> entry.getKey() == null || entry.getValue() == null)
                    || defaults.get(type) == null || !providers.containsKey(defaults.get(type))) {
                throw new NotificationConfigurationException("INVALID_REGISTRATION");
            }
            snapshot.put(type, Map.copyOf(providers));
        });
        this.routes = Map.copyOf(snapshot);
        this.defaults = Map.copyOf(defaults);
    }

    @Override
    public SendReceipt send(Notification notification) {
        requireNotification(notification);
        ProviderId provider = defaults.get(notification.getClass());
        if (provider == null) {
            throw new NotificationConfigurationException("UNREGISTERED_CHANNEL");
        }
        return send(notification, provider);
    }

    @Override
    public SendReceipt send(Notification notification, ProviderId providerId) {
        requireNotification(notification);
        if (providerId == null) {
            throw new NotificationConfigurationException("PROVIDER_REQUIRED");
        }
        var providers = routes.get(notification.getClass());
        if (providers == null || !providers.containsKey(providerId)) {
            throw new NotificationConfigurationException("UNREGISTERED_PROVIDER");
        }
        SendReceipt receipt = providers.get(providerId).send(notification);
        if (receipt == null || !providerId.equals(receipt.providerId())) {
            throw new NotificationDeliveryException(providerId, FailureCategory.INVALID_RESPONSE);
        }
        return receipt;
    }

    private static void requireNotification(Notification notification) {
        if (notification == null) {
            throw new NotificationValidationException("notification", "REQUIRED");
        }
    }
}
