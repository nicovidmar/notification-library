package dev.challenge.notifications.channels.push;

import dev.challenge.notifications.domain.Notification;
import dev.challenge.notifications.domain.NotificationValidationException;

/** One recipient and immutable content; no delivery guarantee. */
public record PushNotification(DeviceToken to, String title, String body) implements Notification {
    public PushNotification {
        if (to == null) {
            throw new NotificationValidationException("to", "REQUIRED");
        }
        if (title == null || title.isBlank()) {
            throw new NotificationValidationException("title", "REQUIRED");
        }
        if (body == null || body.isBlank()) {
            throw new NotificationValidationException("body", "REQUIRED");
        }
    }

    @Override
    public String toString() {
        return "PushNotification[REDACTED]";
    }
}
