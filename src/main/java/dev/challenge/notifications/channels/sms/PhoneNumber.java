package dev.challenge.notifications.channels.sms;

import dev.challenge.notifications.domain.NotificationValidationException;

/** Immutable recipient; syntax validation does not establish deliverability. */
public record PhoneNumber(String value) {
    public PhoneNumber {
        if (value == null || !value.matches("\\+[1-9][0-9]{1,14}")) {
            throw new NotificationValidationException("to", "INVALID_RECIPIENT");
        }
    }

    @Override
    public String toString() {
        return "PhoneNumber[REDACTED]";
    }
}
