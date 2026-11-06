package dev.challenge.notifications.adapters.fcm;

import dev.challenge.notifications.channels.push.PushNotification;
import dev.challenge.notifications.domain.FailureCategory;
import dev.challenge.notifications.domain.NotificationConfigurationException;
import dev.challenge.notifications.domain.NotificationDeliveryException;
import dev.challenge.notifications.domain.NotificationValidationException;
import dev.challenge.notifications.domain.SendReceipt;
import dev.challenge.notifications.spi.NotificationSender;
import java.io.IOException;
import java.util.Optional;

/**
 * Local simulation only: never performs HTTP, retries or fallback.
 * Concurrent use requires a thread-safe access-token supplier.
 * Successful receipts represent acceptance with fixed simulated identifiers, not delivery.
 */
public final class SimulatedFcmSender implements NotificationSender<PushNotification> {
    private final FcmConfig config;
    private final FcmClient client;

    /** Builds a local sender; null configuration or scenario is a configuration error. */
    public SimulatedFcmSender(FcmConfig config, FcmSimulation scenario) {
        this(config, scenario == null ? null : scenario.client());
    }

    // Internal request-capture seam; real transports are intentionally not part of this public API.
    SimulatedFcmSender(FcmConfig config, FcmClient client) {
        if (config == null || client == null) {
            throw new NotificationConfigurationException("INVALID_FCM_CONFIG");
        }
        this.config = config;
        this.client = client;
    }

    @Override
    public SendReceipt send(PushNotification notification) {
        if (notification == null) {
            throw new NotificationValidationException("notification", "REQUIRED");
        }
        String token = config.accessToken().get();
        if (token == null || token.isBlank() || token.contains("\r") || token.contains("\n")) {
            throw new NotificationConfigurationException("INVALID_FCM_TOKEN");
        }
        var request = new FcmRequest(config.projectId(), notification.to().value(),
                notification.title(), notification.body());
        final FcmResponse response;
        try {
            response = client.send(token, request);
        } catch (IOException failure) {
            // Transport text can contain credentials or content; do not chain it.
            throw failure(FailureCategory.UNKNOWN_OUTCOME);
        }
        if (response == null) {
            throw failure(FailureCategory.INVALID_RESPONSE);
        }
        if (response.status() == 200 && response.name() != null
                && response.name().matches("projects/" + config.projectId() + "/messages/[^/\\s]+")
                && (response.errorCode() == null || response.errorCode().isBlank())) {
            return new SendReceipt(config.providerId(), Optional.of(response.name()), true);
        }
        throw failure(category(response.status()));
    }

    private static FailureCategory category(int status) {
        if (status == 401 || status == 403) return FailureCategory.AUTHENTICATION;
        if (status == 429) return FailureCategory.RATE_LIMIT;
        if (status >= 400 && status < 500) return FailureCategory.REJECTED;
        if (status >= 500 && status < 600) return FailureCategory.UNAVAILABLE;
        return FailureCategory.INVALID_RESPONSE;
    }

    private NotificationDeliveryException failure(FailureCategory category) {
        return new NotificationDeliveryException(config.providerId(), category);
    }
}
