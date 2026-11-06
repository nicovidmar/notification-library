package dev.challenge.notifications.domain;

import java.util.Objects;

/** Invalid wiring or provider settings. The code must not contain configuration values. */
public final class NotificationConfigurationException extends NotificationException {
    private final String code;

    public NotificationConfigurationException(String code) {
        super("Invalid notification configuration: " + code);
        this.code = Objects.requireNonNull(code);
    }

    public String code() { return code; }
}
