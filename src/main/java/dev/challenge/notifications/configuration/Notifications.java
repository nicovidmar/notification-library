package dev.challenge.notifications.configuration;

import dev.challenge.notifications.api.NotificationClient;
import dev.challenge.notifications.application.DefaultNotificationClient;
import dev.challenge.notifications.domain.*;
import dev.challenge.notifications.spi.NotificationSender;

import java.util.HashMap;
import java.util.Map;

/** Java-only composition API; no discovery, global state or provider-specific dependencies. */
public final class Notifications {
    private Notifications() { }

    public static Builder builder() {
        return new Builder();
    }

    /** Mutable, single-threaded builder. Built clients retain independent routing snapshots. */
    public static final class Builder {
        private final Map<Class<? extends Notification>, Map<ProviderId, NotificationSender<Notification>>> routes
                = new HashMap<>();
        private final Map<Class<? extends Notification>, ProviderId> defaults = new HashMap<>();

        private Builder() { }

        /** Registers a provider for an exact notification type; rejects nulls and duplicate keys. */
        public <N extends Notification> Builder register(Class<N> type, ProviderId id, NotificationSender<N> sender) {
            if (type == null || id == null || sender == null) {
                throw new NotificationConfigurationException("REGISTRATION_REQUIRED");
            }
            var providers = routes.computeIfAbsent(type, ignored -> new HashMap<>());
            if (providers.containsKey(id)) {
                throw new NotificationConfigurationException("DUPLICATE_PROVIDER");
            }
            // Capture the compatible type/sender pair. No unchecked generic cast is required.
            providers.put(id, notification -> sender.send(type.cast(notification)));
            return this;
        }

        /** Declares a default once; its registration may be added later, before build(). */
        public Builder defaultProvider(Class<? extends Notification> type, ProviderId id) {
            if (type == null || id == null) {
                throw new NotificationConfigurationException("DEFAULT_REQUIRED");
            }
            if (defaults.containsKey(type)) {
                throw new NotificationConfigurationException("DUPLICATE_DEFAULT");
            }
            defaults.put(type, id);
            return this;
        }

        /** Validates all default references and returns an immutable routing snapshot. */
        public NotificationClient build() {
            return new DefaultNotificationClient(routes, defaults);
        }
    }
}
