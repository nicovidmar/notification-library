package dev.challenge.notifications.adapters.fcm;

/** Local representation of provider fields, not a serialized HTTP request. */
record FcmRequest(String projectId, String token, String title, String body) {
    @Override
    public String toString() {
        return "FcmRequest[REDACTED]";
    }
}
