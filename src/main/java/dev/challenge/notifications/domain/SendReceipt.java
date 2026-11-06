package dev.challenge.notifications.domain;

import java.util.Optional;

/**
 * Acceptance of an attempt, not confirmation of delivery.
 * A missing remote ID is valid. Sender authors must validate provider data before construction.
 */
public record SendReceipt(ProviderId providerId, Optional<String> providerMessageId, boolean simulated) {
    public SendReceipt {
        if (providerId == null || providerMessageId == null
                || providerMessageId.filter(String::isBlank).isPresent()) {
            throw new IllegalArgumentException("Invalid receipt fields");
        }
    }

    @Override
    public String toString() {
        return "SendReceipt[providerId=" + providerId + ", providerMessageId=[REDACTED], simulated=" + simulated + "]";
    }
}
