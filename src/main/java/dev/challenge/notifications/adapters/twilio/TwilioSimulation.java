package dev.challenge.notifications.adapters.twilio;

import java.io.IOException;

/** Fixed local fixtures; identifiers are not unique or idempotency keys. */
public enum TwilioSimulation {
    ACCEPTED,
    REJECTED,
    UNAUTHORIZED,
    RATE_LIMITED,
    UNAVAILABLE,
    UNKNOWN_OUTCOME;

    TwilioClient client() {
        return (key, secret, request) -> {
            if (this == UNKNOWN_OUTCOME) {
                throw new IOException("Simulated transport failure");
            }
            if (this == ACCEPTED) {
                return new TwilioResponse(201, "SM_SIMULATED", "queued");
            }
            int status = switch (this) {
                case REJECTED -> 400;
                case UNAUTHORIZED -> 401;
                case RATE_LIMITED -> 429;
                default -> 503;
            };
            return new TwilioResponse(status, null, null);
        };
    }
}
