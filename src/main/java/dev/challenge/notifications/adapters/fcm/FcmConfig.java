package dev.challenge.notifications.adapters.fcm;

import dev.challenge.notifications.domain.NotificationConfigurationException;
import dev.challenge.notifications.domain.ProviderId;
import java.util.function.Supplier;

/**
 * FCM settings supplied in Java; no ADC, files or environment variables are consulted.
 * The supplier is shared with the caller, so it must be thread-safe for concurrent sends.
 * Its unexpected exceptions propagate; it is responsible for safe diagnostics.
 * @param providerId routing identity used when registering this sender
 * @param projectId nonempty project identifier using ASCII letters, digits and hyphens
 * @param accessToken supplies a token per attempt; rotation and ownership remain with the caller
 */
public record FcmConfig(ProviderId providerId, String projectId, Supplier<String> accessToken) {
    public FcmConfig {
        if (providerId == null || projectId == null || !projectId.matches("[A-Za-z0-9-]+")
                || accessToken == null) {
            throw new NotificationConfigurationException("INVALID_FCM_CONFIG");
        }
    }

    @Override
    public String toString() {
        return "FcmConfig[REDACTED]";
    }
}
