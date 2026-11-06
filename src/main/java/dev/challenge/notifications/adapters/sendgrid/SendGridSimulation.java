package dev.challenge.notifications.adapters.sendgrid;

import java.io.IOException;

/** Deterministic local outcomes. No network request or real credential verification takes place. */
public enum SendGridSimulation {
    ACCEPTED(202),
    REJECTED(400),
    UNAUTHORIZED(401),
    FORBIDDEN(403),
    RATE_LIMITED(429),
    UNAVAILABLE(503),
    UNKNOWN_OUTCOME(0);

    private final int statusCode;

    SendGridSimulation(int statusCode) {
        this.statusCode = statusCode;
    }

    SendGridClient client() {
        return (key, request) -> {
            if (this == UNKNOWN_OUTCOME) {
                throw new IOException("Simulated indeterminate transport outcome");
            }
            return new SendGridResponse(statusCode);
        };
    }
}
