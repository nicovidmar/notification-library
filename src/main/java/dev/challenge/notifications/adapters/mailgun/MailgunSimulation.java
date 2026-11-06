package dev.challenge.notifications.adapters.mailgun;

import java.io.IOException;

/** Deterministic local scenarios. The fixed accepted ID is a fixture, not a unique correlation key. */
public enum MailgunSimulation {
    ACCEPTED(200), REJECTED(400), UNAUTHORIZED(401), FORBIDDEN(403),
    RATE_LIMITED(429), UNAVAILABLE(503), UNKNOWN_OUTCOME(0);

    private final int statusCode;

    MailgunSimulation(int statusCode) { this.statusCode = statusCode; }

    MailgunClient client() {
        return (username, key, request) -> {
            if (this == UNKNOWN_OUTCOME) {
                throw new IOException("Simulated indeterminate transport outcome");
            }
            if (this == ACCEPTED) {
                return new MailgunResponse(200, "<simulated-mailgun@example.invalid>", "Queued for simulation");
            }
            return new MailgunResponse(statusCode, null, "Simulated rejection");
        };
    }
}
