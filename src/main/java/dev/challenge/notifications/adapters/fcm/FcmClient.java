package dev.challenge.notifications.adapters.fcm;

import java.io.IOException;

@FunctionalInterface
interface FcmClient {
    FcmResponse send(String accessToken, FcmRequest request) throws IOException;
}
