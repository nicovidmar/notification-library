package dev.challenge.notifications.adapters.twilio;

import dev.challenge.notifications.channels.sms.SmsNotification;
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
 * Public scenarios are immutable and thread-safe.
 * Successful receipts represent acceptance with fixed simulated identifiers, not delivery.
 */
public final class SimulatedTwilioSender implements NotificationSender<SmsNotification> {
    private final TwilioConfig config;
    private final TwilioClient client;

    /** Builds a local sender; null configuration or scenario is a configuration error. */
    public SimulatedTwilioSender(TwilioConfig config, TwilioSimulation scenario) {
        this(config, scenario == null ? null : scenario.client());
    }

    // Internal request-capture seam; real transports are intentionally not part of this public API.
    SimulatedTwilioSender(TwilioConfig config, TwilioClient client) {
        if (config == null || client == null) {
            throw new NotificationConfigurationException("INVALID_TWILIO_CONFIG");
        }
        this.config = config;
        this.client = client;
    }

    @Override
    public SendReceipt send(SmsNotification notification) {
        if (notification == null) {
            throw new NotificationValidationException("notification", "REQUIRED");
        }
        var request = new TwilioRequest(config.accountSid(), config.from().value(),
                notification.to().value(), notification.body());
        final TwilioResponse response;
        try {
            response = client.send(config.apiKeySid(), config.secret(), request);
        } catch (IOException failure) {
            // Transport text can contain credentials or content; do not chain it.
            throw failure(FailureCategory.UNKNOWN_OUTCOME);
        }
        if (response == null) {
            throw failure(FailureCategory.INVALID_RESPONSE);
        }
        if (response.status() == 201 && response.sid() != null && !response.sid().isBlank()
                && "queued".equals(response.state())) {
            return new SendReceipt(config.providerId(), Optional.of(response.sid()), true);
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
