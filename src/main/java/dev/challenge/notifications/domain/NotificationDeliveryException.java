package dev.challenge.notifications.domain;

import java.util.Objects;

/** A rejected, unavailable, indeterminate or malformed provider outcome; never implies safe retry. */
public final class NotificationDeliveryException extends NotificationException {
    private final ProviderId providerId;
    private final FailureCategory category;

    public NotificationDeliveryException(ProviderId providerId, FailureCategory category) {
        super("Notification attempt failed: " + category + " (provider=" + providerId + ")");
        this.providerId = Objects.requireNonNull(providerId);
        this.category = Objects.requireNonNull(category);
    }

    public ProviderId providerId() { return providerId; }
    public FailureCategory category() { return category; }
}
