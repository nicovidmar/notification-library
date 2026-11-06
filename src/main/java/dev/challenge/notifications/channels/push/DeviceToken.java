package dev.challenge.notifications.channels.push;

import dev.challenge.notifications.domain.NotificationValidationException;

/** Immutable recipient; syntax validation does not establish deliverability. */
public record DeviceToken(String value) {
    public DeviceToken {
        if (value == null || value.isBlank()) {
            throw new NotificationValidationException("to", "INVALID_RECIPIENT");
        }
    }

    @Override
    public String toString() {
        return "DeviceToken[REDACTED]";
    }
}
