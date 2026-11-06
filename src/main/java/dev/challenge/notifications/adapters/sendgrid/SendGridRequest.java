package dev.challenge.notifications.adapters.sendgrid;

import java.util.List;

/** Mirrors the supported SendGrid JSON fields; serialization is intentionally outside this simulation. */
record SendGridRequest(List<Personalization> personalizations, Address from, String subject, List<Content> content) {
    SendGridRequest {
        personalizations = List.copyOf(personalizations);
        content = List.copyOf(content);
    }

    record Address(String email) {
        @Override
        public String toString() { return "Address[REDACTED]"; }
    }

    record Personalization(List<Address> to) {
        Personalization { to = List.copyOf(to); }
        @Override
        public String toString() { return "Personalization[REDACTED]"; }
    }

    record Content(String type, String value) {
        @Override
        public String toString() { return "Content[REDACTED]"; }
    }

    @Override
    public String toString() {
        return "SendGridRequest[REDACTED]";
    }
}
