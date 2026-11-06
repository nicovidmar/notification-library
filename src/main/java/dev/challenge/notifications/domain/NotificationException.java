package dev.challenge.notifications.domain;

/** Base class for expected notification failures; unchecked and catchable by clients. */
public abstract class NotificationException extends RuntimeException {
    protected NotificationException(String message) {
        super(message);
    }
}
