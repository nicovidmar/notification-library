package dev.challenge.notifications.adapters.slack;

import java.io.IOException;

/** Internal simulation seam, not a public HTTP transport. */
@FunctionalInterface
interface SlackClient {
    SlackResponse send(String webhookUrl, SlackRequest request) throws IOException;
}
