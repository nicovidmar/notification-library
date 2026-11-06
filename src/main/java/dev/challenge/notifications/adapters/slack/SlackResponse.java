package dev.challenge.notifications.adapters.slack;

/** Webhook responses use a plain-text body, not Web API JSON. */
record SlackResponse(int statusCode, String body) {
    @Override
    public String toString() {
        return "SlackResponse[statusCode=" + statusCode + ", body=[REDACTED]]";
    }
}
