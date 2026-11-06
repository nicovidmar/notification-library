package dev.challenge.notifications.channels.slack;

import dev.challenge.notifications.domain.Notification;
import dev.challenge.notifications.domain.NotificationValidationException;

/** Plain text for the destination associated with the configured webhook. */
public record SlackNotification(String text) implements Notification {
    public SlackNotification {
        if (text == null || text.isBlank()) {
            throw new NotificationValidationException("text", "REQUIRED");
        }
    }

    @Override
    public String toString() {
        return "SlackNotification[REDACTED]";
    }
}
