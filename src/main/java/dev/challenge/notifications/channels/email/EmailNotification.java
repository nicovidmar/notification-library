package dev.challenge.notifications.channels.email;

import dev.challenge.notifications.domain.Notification;
import dev.challenge.notifications.domain.NotificationValidationException;

/** One recipient and a plain-text body. Content is preserved without trimming. */
public record EmailNotification(EmailAddress to, String subject, String body) implements Notification {
    public EmailNotification {
        if (to == null) {
            throw new NotificationValidationException("to", "REQUIRED");
        }
        if (subject == null || subject.isBlank() || subject.contains("\r") || subject.contains("\n")) {
            throw new NotificationValidationException("subject", "INVALID_SUBJECT");
        }
        if (body == null || body.isBlank()) {
            throw new NotificationValidationException("body", "REQUIRED");
        }
    }

    @Override
    public String toString() {
        return "EmailNotification[to=[REDACTED], subject=[REDACTED], body=[REDACTED]]";
    }
}
