package dev.challenge.notifications.channels.sms;

import dev.challenge.notifications.domain.Notification;
import dev.challenge.notifications.domain.NotificationValidationException;

/** One recipient and immutable content; no delivery guarantee. */
public record SmsNotification(PhoneNumber to, String body) implements Notification {
    public SmsNotification {
        if (to == null) {
            throw new NotificationValidationException("to", "REQUIRED");
        }
        if (body == null || body.isBlank()) {
            throw new NotificationValidationException("body", "REQUIRED");
        }
    }

    @Override
    public String toString() {
        return "SmsNotification[REDACTED]";
    }
}
