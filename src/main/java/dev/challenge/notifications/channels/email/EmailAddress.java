package dev.challenge.notifications.channels.email;

import dev.challenge.notifications.domain.NotificationValidationException;

/** Immutable ASCII email address in the supported subset; does not verify deliverability. */
public record EmailAddress(String value) {
    public EmailAddress {
        if (!isSupported(value)) {
            throw new NotificationValidationException("to", "INVALID_EMAIL");
        }
    }

    private static boolean isSupported(String value) {
        if (value == null || value.length() > 254) {
            return false;
        }
        int separator = value.indexOf('@');
        if (separator < 1 || separator > 64 || separator != value.lastIndexOf('@')) {
            return false;
        }
        String local = value.substring(0, separator);
        if (!local.matches("[A-Za-z0-9._%+-]+") || local.startsWith(".")
                || local.endsWith(".") || local.contains("..")) {
            return false;
        }
        String[] labels = value.substring(separator + 1).split("\\.", -1);
        if (labels.length < 2) {
            return false;
        }
        for (String label : labels) {
            if (!label.matches("[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?")) {
                return false;
            }
        }
        return true;
    }

    @Override
    public String toString() {
        return "EmailAddress[REDACTED]";
    }
}
