package dev.challenge.notifications.adapters.fcm;

import java.io.IOException;

/** Fixed local fixtures; identifiers are not unique or idempotency keys. */
public enum FcmSimulation {
    ACCEPTED,
    REJECTED,
    UNAUTHORIZED,
    RATE_LIMITED,
    UNAVAILABLE,
    UNKNOWN_OUTCOME;

    FcmClient client() {
        return (token, request) -> {
            if (this == UNKNOWN_OUTCOME) {
                throw new IOException("Simulated transport failure");
            }
            if (this == ACCEPTED) {
                return new FcmResponse(200, "projects/" + request.projectId() + "/messages/simulated", null);
            }
            int status = switch (this) {
                case REJECTED -> 400;
                case UNAUTHORIZED -> 401;
                case RATE_LIMITED -> 429;
                default -> 503;
            };
            return new FcmResponse(status, null, null);
        };
    }
}
