package dev.challenge.notifications.adapters.twilio;

/** Local representation of provider fields, not a serialized HTTP request. */
record TwilioRequest(String accountSid, String from, String to, String body) {
    @Override
    public String toString() {
        return "TwilioRequest[REDACTED]";
    }
}
