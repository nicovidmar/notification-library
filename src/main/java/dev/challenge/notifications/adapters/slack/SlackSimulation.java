package dev.challenge.notifications.adapters.slack;

import java.io.IOException;

/** Deterministic local outcomes without authentication or network access. */
public enum SlackSimulation {
    ACCEPTED(200, "ok"),
    REJECTED(400, "invalid_payload"),
    UNAUTHORIZED(403, "invalid_token"),
    FORBIDDEN(403, "action_prohibited"),
    RATE_LIMITED(429, "rate_limited"),
    UNAVAILABLE(503, "unavailable"),
    UNKNOWN_OUTCOME(0, null);

    private final int statusCode;
    private final String body;

    SlackSimulation(int statusCode, String body) {
        this.statusCode = statusCode;
        this.body = body;
    }

    SlackClient client() {
        return (url, request) -> {
            if (this == UNKNOWN_OUTCOME) {
                throw new IOException("Simulated indeterminate transport outcome");
            }
            return new SlackResponse(statusCode, body);
        };
    }
}
