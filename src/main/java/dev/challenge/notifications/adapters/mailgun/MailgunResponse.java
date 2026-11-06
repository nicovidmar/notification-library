package dev.challenge.notifications.adapters.mailgun;

/** Provider outcome before validation. Both id and message are required for an accepted response. */
record MailgunResponse(int statusCode, String id, String message) {
    @Override
    public String toString() {
        return "MailgunResponse[statusCode=" + statusCode + ", id=[REDACTED], message=[REDACTED]]";
    }
}
