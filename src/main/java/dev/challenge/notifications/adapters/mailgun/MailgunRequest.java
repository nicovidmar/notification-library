package dev.challenge.notifications.adapters.mailgun;

/** Domain path parameter and supported multipart form fields; no wire serialization or HTTP. */
record MailgunRequest(String domain, String from, String to, String subject, String text) {
    @Override
    public String toString() { return "MailgunRequest[REDACTED]"; }
}
