package dev.challenge.notifications.domain;

/** Stable failure classifications, independent of provider response formats. */
public enum FailureCategory {
    AUTHENTICATION,
    RATE_LIMIT,
    REJECTED,
    UNAVAILABLE,
    UNKNOWN_OUTCOME,
    INVALID_RESPONSE
}
