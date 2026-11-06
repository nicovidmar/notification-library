package dev.challenge.notifications.adapters.sendgrid;

import java.io.IOException;

/** Internal seam for local simulation and request-capture tests, not an HTTP integration. */
@FunctionalInterface
interface SendGridClient {
    SendGridResponse send(String apiKey, SendGridRequest request) throws IOException;
}
