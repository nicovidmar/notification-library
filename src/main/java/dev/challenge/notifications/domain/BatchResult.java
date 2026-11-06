package dev.challenge.notifications.domain;

import java.util.Objects;

/** One batch entry's outcome; its position matches the input position. */
public sealed interface BatchResult {
    /** Provider acceptance, not delivery confirmation. */
    record Accepted(SendReceipt receipt) implements BatchResult {
        public Accepted {
            Objects.requireNonNull(receipt, "receipt");
        }
    }

    /** Failure of one attempt, including executor rejection. Inspect causes without logging raw data. */
    record Failed(Throwable cause) implements BatchResult {
        public Failed {
            Objects.requireNonNull(cause, "cause");
        }

        @Override
        public String toString() {
            return "Failed[cause=[REDACTED]]";
        }
    }
}
