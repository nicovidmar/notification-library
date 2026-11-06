package dev.challenge.notifications.adapters.mailgun;

import java.io.IOException;

/** Internal simulation seam: models Basic credentials separately from the form fields. */
@FunctionalInterface
interface MailgunClient {
    MailgunResponse send(String username, String apiKey, MailgunRequest request) throws IOException;
}
