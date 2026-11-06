package dev.challenge.notifications.adapters.twilio;

record TwilioResponse(int status, String sid, String state) {
    @Override
    public String toString() {
        return "TwilioResponse[REDACTED]";
    }
}
