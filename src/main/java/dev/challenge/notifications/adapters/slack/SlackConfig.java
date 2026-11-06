package dev.challenge.notifications.adapters.slack;

import dev.challenge.notifications.domain.NotificationConfigurationException;
import dev.challenge.notifications.domain.ProviderId;

/** Secret webhook URL supplied in Java. Use fictional credentials in this simulator. */
public record SlackConfig(ProviderId providerId, String webhookUrl) {
    public SlackConfig {
        if (providerId == null || webhookUrl == null
                || !webhookUrl.matches("https://hooks[.]slack[.]com/services/[A-Za-z0-9_-]+/[A-Za-z0-9_-]+/[A-Za-z0-9_-]+")) {
            throw new NotificationConfigurationException("INVALID_SLACK_CONFIG");
        }
    }

    @Override
    public String toString() {
        return "SlackConfig[providerId=" + providerId + ", webhookUrl=[REDACTED]]";
    }
}
