package dev.challenge.notifications.adapters.sendgrid;

import dev.challenge.notifications.channels.email.EmailAddress;
import dev.challenge.notifications.domain.NotificationConfigurationException;
import dev.challenge.notifications.domain.ProviderId;

/** Provider settings supplied by the caller. Use fictional credentials with this simulator. */
public record SendGridConfig(ProviderId providerId, String apiKey, EmailAddress from) {
    public SendGridConfig {
        if (providerId == null || from == null || apiKey == null || apiKey.isBlank()
                || apiKey.contains("\r") || apiKey.contains("\n")) {
            throw new NotificationConfigurationException("INVALID_SENDGRID_CONFIG");
        }
    }

    @Override
    public String toString() {
        return "SendGridConfig[providerId=" + providerId + ", apiKey=[REDACTED], from=[REDACTED]]";
    }
}
