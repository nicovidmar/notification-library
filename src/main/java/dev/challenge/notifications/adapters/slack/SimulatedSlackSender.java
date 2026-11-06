package dev.challenge.notifications.adapters.slack;

import dev.challenge.notifications.channels.slack.SlackNotification;
import dev.challenge.notifications.domain.FailureCategory;
import dev.challenge.notifications.domain.NotificationConfigurationException;
import dev.challenge.notifications.domain.NotificationDeliveryException;
import dev.challenge.notifications.domain.NotificationValidationException;
import dev.challenge.notifications.domain.SendReceipt;
import dev.challenge.notifications.spi.NotificationSender;

import java.io.IOException;
import java.util.Optional;
import java.util.Set;

/** Thread-safe with public simulation scenarios. No network access or implicit retries. */
public final class SimulatedSlackSender implements NotificationSender<SlackNotification> {
    private static final Set<String> AUTH_ERRORS = Set.of(
            "invalid_token", "no_active_hooks", "no_service", "no_service_id");
    private final SlackConfig config;
    private final SlackClient client;

    public SimulatedSlackSender(SlackConfig config, SlackSimulation simulation) {
        this(config, simulationClient(simulation));
    }

    SimulatedSlackSender(SlackConfig config, SlackClient client) {
        if (config == null || client == null) {
            throw new NotificationConfigurationException("INVALID_SLACK_CONFIG");
        }
        this.config = config;
        this.client = client;
    }

    private static SlackClient simulationClient(SlackSimulation simulation) {
        if (simulation == null) {
            throw new NotificationConfigurationException("SIMULATION_REQUIRED");
        }
        return simulation.client();
    }

    @Override
    public SendReceipt send(SlackNotification notification) {
        if (notification == null) {
            throw new NotificationValidationException("notification", "REQUIRED");
        }
        String text = notification.text().replace("&", "&amp;")
                .replace("<", "&lt;").replace(">", "&gt;");
        final SlackResponse response;
        try {
            response = client.send(config.webhookUrl(), new SlackRequest(text, false));
        } catch (IOException failure) {
            // External exceptions may contain authenticated URLs or payloads.
            throw new NotificationDeliveryException(config.providerId(), FailureCategory.UNKNOWN_OUTCOME);
        }
        if (response == null) {
            throw new NotificationDeliveryException(config.providerId(), FailureCategory.INVALID_RESPONSE);
        }
        if (response.statusCode() == 200 && "ok".equals(response.body())) {
            return new SendReceipt(config.providerId(), Optional.empty(), true);
        }
        throw new NotificationDeliveryException(config.providerId(), category(response));
    }

    private static FailureCategory category(SlackResponse response) {
        int status = response.statusCode();
        if (status == 429) return FailureCategory.RATE_LIMIT;
        if (status >= 500 && status < 600) return FailureCategory.UNAVAILABLE;
        if (status >= 400 && status < 500) {
            if (status == 401 || (response.body() != null && AUTH_ERRORS.contains(response.body()))) {
                return FailureCategory.AUTHENTICATION;
            }
            return FailureCategory.REJECTED;
        }
        return FailureCategory.INVALID_RESPONSE;
    }
}
