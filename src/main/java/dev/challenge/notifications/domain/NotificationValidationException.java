package dev.challenge.notifications.domain;

import java.util.Objects;

/** Invalid notification data. Field and code must be safe identifiers, never input values. */
public final class NotificationValidationException extends NotificationException {
    private final String field;
    private final String code;

    public NotificationValidationException(String field, String code) {
        super("Invalid notification field: " + field + " (" + code + ")");
        this.field = Objects.requireNonNull(field);
        this.code = Objects.requireNonNull(code);
    }

    public String field() { return field; }
    public String code() { return code; }
}
