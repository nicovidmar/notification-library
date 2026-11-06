package dev.challenge.notifications.adapters.mailgun;

import dev.challenge.notifications.channels.email.EmailAddress;
import dev.challenge.notifications.domain.NotificationConfigurationException;
import dev.challenge.notifications.domain.ProviderId;

/** Caller-supplied settings. Domain is syntax-checked only; use fictional credentials in simulations. */
public record MailgunConfig(ProviderId providerId, String domain, String apiKey, EmailAddress from) {
    public MailgunConfig {
        if (providerId == null || from == null || !validDomain(domain)
                || apiKey == null || apiKey.isBlank() || apiKey.contains("\r") || apiKey.contains("\n")) {
            throw new NotificationConfigurationException("INVALID_MAILGUN_CONFIG");
        }
    }

    private static boolean validDomain(String domain) {
        if (domain == null || domain.length() > 253) return false;
        String[] labels = domain.split("\\.", -1);
        if (labels.length < 2) return false;
        for (String label : labels) {
            if (!label.matches("[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?")) return false;
        }
        return true;
    }

    @Override
    public String toString() {
        return "MailgunConfig[providerId=" + providerId + ", domain=[REDACTED], apiKey=[REDACTED], from=[REDACTED]]";
    }
}
