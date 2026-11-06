package dev.challenge.notifications.adapters.slack;

/** Supported JSON fields; serialization is outside this simulation. */
record SlackRequest(String text, boolean mrkdwn) {
    @Override
    public String toString() {
        return "SlackRequest[REDACTED]";
    }
}
