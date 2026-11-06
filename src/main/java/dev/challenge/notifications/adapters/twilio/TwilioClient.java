package dev.challenge.notifications.adapters.twilio;

import java.io.IOException;

@FunctionalInterface
interface TwilioClient {
    TwilioResponse send(String apiKeySid, String secret, TwilioRequest request) throws IOException;
}
