package dev.challenge.notifications.domain;

/** A non-secret routing name, scoped to a notification type. */
public record ProviderId(String value) {
    public ProviderId {
        if (value == null || !value.matches("[a-z][a-z0-9-]{0,63}")) {
            throw new NotificationConfigurationException("INVALID_PROVIDER_ID");
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
