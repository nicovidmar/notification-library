package dev.challenge.notifications.domain;

import java.util.Objects;

/**
 * Minimal observation of a completed send attempt, not proof of delivery.
 * Contains no recipient, content, credentials, remote ID or raw exception.
 */
public sealed interface NotificationEvent {
    ProviderId providerId();

    /** The client returned a valid acceptance receipt. */
    record Accepted(ProviderId providerId, boolean simulated) implements NotificationEvent {
        public Accepted {
            Objects.requireNonNull(providerId, "providerId");
        }
    }

    /** A typed delivery failure occurred; UNKNOWN_OUTCOME still leaves acceptance uncertain. */
    record Failed(ProviderId providerId, FailureCategory category) implements NotificationEvent {
        public Failed {
            Objects.requireNonNull(providerId, "providerId");
            Objects.requireNonNull(category, "category");
        }
    }
}
