package dev.challenge.notifications.adapters.fcm;

record FcmResponse(int status, String name, String errorCode) {
    @Override
    public String toString() {
        return "FcmResponse[REDACTED]";
    }
}
