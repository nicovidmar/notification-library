package dev.challenge.notifications.adapters.mailgun;

import dev.challenge.notifications.channels.email.EmailNotification;
import dev.challenge.notifications.domain.*;
import dev.challenge.notifications.spi.NotificationSender;
import java.io.IOException;
import java.util.Optional;

/**
 * Local Mailgun adapter, thread-safe when constructed with a public simulation scenario.
 * Maps one plain-text recipient; never performs HTTP, retries or fallback.
 */
public final class SimulatedMailgunSender implements NotificationSender<EmailNotification> {
    private final MailgunConfig config;
    private final MailgunClient client;

    public SimulatedMailgunSender(MailgunConfig config, MailgunSimulation simulation) {
        this(config, simulationClient(simulation));
    }

    // Package-private request-capture seam, not a public real-transport extension.
    SimulatedMailgunSender(MailgunConfig config, MailgunClient client) {
        if (config == null || client == null) {
            throw new NotificationConfigurationException("INVALID_MAILGUN_CONFIG");
        }
        this.config = config;
        this.client = client;
    }

    private static MailgunClient simulationClient(MailgunSimulation simulation) {
        if (simulation == null) throw new NotificationConfigurationException("SIMULATION_REQUIRED");
        return simulation.client();
    }

    @Override
    public SendReceipt send(EmailNotification notification) {
        if (notification == null) throw new NotificationValidationException("notification", "REQUIRED");
        var request = new MailgunRequest(config.domain(), config.from().value(),
                notification.to().value(), notification.subject(), notification.body());
        final MailgunResponse response;
        try {
            response = client.send("api", config.apiKey(), request);
        } catch (IOException failure) {
            // Raw transport text may contain credentials or payloads.
            throw new NotificationDeliveryException(config.providerId(), FailureCategory.UNKNOWN_OUTCOME);
        }
        if (response == null) throw invalidResponse();
        if (response.statusCode() == 200) {
            if (response.id() == null || response.id().isBlank()
                    || response.message() == null || response.message().isBlank()) {
                throw invalidResponse();
            }
            return new SendReceipt(config.providerId(), Optional.of(response.id()), true);
        }
        throw new NotificationDeliveryException(config.providerId(), category(response.statusCode()));
    }

    private NotificationDeliveryException invalidResponse() {
        return new NotificationDeliveryException(config.providerId(), FailureCategory.INVALID_RESPONSE);
    }

    private static FailureCategory category(int status) {
        if (status == 401 || status == 403) return FailureCategory.AUTHENTICATION;
        if (status == 429) return FailureCategory.RATE_LIMIT;
        if (status >= 400 && status < 500) return FailureCategory.REJECTED;
        if (status >= 500 && status < 600) return FailureCategory.UNAVAILABLE;
        return FailureCategory.INVALID_RESPONSE;
    }
}
