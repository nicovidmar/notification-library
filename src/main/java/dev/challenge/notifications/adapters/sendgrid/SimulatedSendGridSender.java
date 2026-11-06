package dev.challenge.notifications.adapters.sendgrid;

import dev.challenge.notifications.channels.email.EmailNotification;
import dev.challenge.notifications.domain.*;
import dev.challenge.notifications.spi.NotificationSender;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

/**
 * Thread-safe when built with a public SendGridSimulation scenario. Sends nothing over the network.
 * Maps one plain-text Email notification and translates known failures; never retries.
 */
public final class SimulatedSendGridSender implements NotificationSender<EmailNotification> {
    private final SendGridConfig config;
    private final SendGridClient client;

    public SimulatedSendGridSender(SendGridConfig config, SendGridSimulation simulation) {
        this(config, simulationClient(simulation));
    }

    // Internal test seam; deliberately not advertised as a real transport extension.
    SimulatedSendGridSender(SendGridConfig config, SendGridClient client) {
        if (config == null || client == null) {
            throw new NotificationConfigurationException("INVALID_SENDGRID_CONFIG");
        }
        this.config = config;
        this.client = client;
    }

    private static SendGridClient simulationClient(SendGridSimulation simulation) {
        if (simulation == null) {
            throw new NotificationConfigurationException("SIMULATION_REQUIRED");
        }
        return simulation.client();
    }

    @Override
    public SendReceipt send(EmailNotification notification) {
        if (notification == null) {
            throw new NotificationValidationException("notification", "REQUIRED");
        }
        var request = new SendGridRequest(
                List.of(new SendGridRequest.Personalization(
                        List.of(new SendGridRequest.Address(notification.to().value())))),
                new SendGridRequest.Address(config.from().value()),
                notification.subject(),
                List.of(new SendGridRequest.Content("text/plain", notification.body())));
        final SendGridResponse response;
        try {
            response = client.send(config.apiKey(), request);
        } catch (IOException failure) {
            // External exception text may contain credentials or payloads; do not chain it.
            throw new NotificationDeliveryException(config.providerId(), FailureCategory.UNKNOWN_OUTCOME);
        }
        if (response == null) {
            throw new NotificationDeliveryException(config.providerId(), FailureCategory.INVALID_RESPONSE);
        }
        int status = response.statusCode();
        if (status == 202) {
            return new SendReceipt(config.providerId(), Optional.empty(), true);
        }
        throw new NotificationDeliveryException(config.providerId(), category(status));
    }

    private static FailureCategory category(int status) {
        if (status == 401 || status == 403) return FailureCategory.AUTHENTICATION;
        if (status == 429) return FailureCategory.RATE_LIMIT;
        if (status >= 400 && status < 500) return FailureCategory.REJECTED;
        if (status >= 500 && status < 600) return FailureCategory.UNAVAILABLE;
        return FailureCategory.INVALID_RESPONSE;
    }
}
