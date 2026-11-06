package dev.challenge.notifications.adapters.slack;

import dev.challenge.notifications.channels.slack.SlackNotification;
import dev.challenge.notifications.configuration.Notifications;
import dev.challenge.notifications.domain.FailureCategory;
import dev.challenge.notifications.domain.NotificationConfigurationException;
import dev.challenge.notifications.domain.NotificationDeliveryException;
import dev.challenge.notifications.domain.NotificationValidationException;
import dev.challenge.notifications.domain.ProviderId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

class SlackTest {
    private static final ProviderId ID = new ProviderId("slack");
    private static final String URL = "https://hooks.slack.com/services/FAKE/DEMO/not-a-secret";
    private static final SlackConfig CONFIG = new SlackConfig(ID, URL);
    private static final SlackNotification MESSAGE = new SlackNotification("private text");

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\n\t"})
    void requiresText(String text) {
        var error = assertThrows(NotificationValidationException.class, () -> new SlackNotification(text));
        assertEquals("text", error.field());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "http://hooks.slack.com/services/A/B/C",
            "https://evil.example/services/A/B/C", "https://hooks.slack.com/services/A/B/C?secret=x",
            "https://hooks.slack.com/services/A/B/C#fragment", "https://hooks.slack.com/services/A/B/",
            "https://hooks.slack.com/services/A/B/C\n"})
    void rejectsUnsupportedUrlsWithoutLeakingThem(String url) {
        var error = assertThrows(NotificationConfigurationException.class, () -> new SlackConfig(ID, url));
        assertFalse(error.toString().contains("https://"));
    }

    @Test
    void mapsPlainTextAndPreservesOriginalNotification() {
        var message = new SlackNotification("A & <@U123> <!channel> *literal*\nHola 👋 &lt;");
        var sender = new SimulatedSlackSender(CONFIG, (url, request) -> {
            assertEquals(URL, url);
            assertEquals("A &amp; &lt;@U123&gt; &lt;!channel&gt; *literal*\nHola 👋 &amp;lt;", request.text());
            assertFalse(request.mrkdwn());
            assertFalse(request.toString().contains("literal"));
            return new SlackResponse(200, "ok");
        });
        var receipt = sender.send(message);
        assertEquals(ID, receipt.providerId());
        assertTrue(receipt.simulated());
        assertTrue(receipt.providerMessageId().isEmpty());
        assertTrue(message.text().contains("<!channel>"));
    }

    @ParameterizedTest
    @CsvSource({"REJECTED, REJECTED", "UNAUTHORIZED, AUTHENTICATION", "FORBIDDEN, REJECTED",
            "RATE_LIMITED, RATE_LIMIT", "UNAVAILABLE, UNAVAILABLE", "UNKNOWN_OUTCOME, UNKNOWN_OUTCOME"})
    void translatesPublicScenarios(SlackSimulation simulation, FailureCategory expected) {
        var error = assertThrows(NotificationDeliveryException.class,
                () -> new SimulatedSlackSender(CONFIG, simulation).send(MESSAGE));
        assertEquals(expected, error.category());
        assertEquals(ID, error.providerId());
    }

    @ParameterizedTest
    @CsvSource({"200, unexpected, INVALID_RESPONSE", "202, ok, INVALID_RESPONSE",
            "302, ok, INVALID_RESPONSE", "400, no_active_hooks, AUTHENTICATION",
            "404, no_service, AUTHENTICATION", "404, no_service_id, AUTHENTICATION",
            "401, unknown, AUTHENTICATION", "403, action_prohibited, REJECTED",
            "410, channel_is_archived, REJECTED", "400, unknown, REJECTED",
            "429, invalid_token, RATE_LIMIT", "503, invalid_token, UNAVAILABLE"})
    void validatesResponseProtocol(int status, String body, FailureCategory expected) {
        assertFailure(new SlackResponse(status, body), expected);
    }

    @Test
    void missingResponsesAndBodiesAreNotAcceptance() {
        assertFailure(null, FailureCategory.INVALID_RESPONSE);
        assertFailure(new SlackResponse(200, null), FailureCategory.INVALID_RESPONSE);
        assertFailure(new SlackResponse(200, "ok\n"), FailureCategory.INVALID_RESPONSE);
        assertFailure(new SlackResponse(400, null), FailureCategory.REJECTED);
    }

    private void assertFailure(SlackResponse response, FailureCategory expected) {
        var sender = new SimulatedSlackSender(CONFIG, (url, request) -> response);
        assertEquals(expected, assertThrows(NotificationDeliveryException.class,
                () -> sender.send(MESSAGE)).category());
    }

    @Test
    void sanitizesTransportFailuresAndDiagnosticsButPropagatesBugs() {
        var sender = new SimulatedSlackSender(CONFIG, (url, request) -> { throw new IOException(URL); });
        var failure = assertThrows(NotificationDeliveryException.class, () -> sender.send(MESSAGE));
        assertNull(failure.getCause());
        assertFalse(failure.toString().contains(URL));
        assertFalse(CONFIG.toString().contains(URL));
        assertFalse(MESSAGE.toString().contains(MESSAGE.text()));
        assertFalse(new SlackResponse(400, URL).toString().contains(URL));
        var bug = new IllegalStateException("bug");
        var broken = new SimulatedSlackSender(CONFIG, (url, request) -> { throw bug; });
        assertSame(bug, assertThrows(IllegalStateException.class, () -> broken.send(MESSAGE)));
    }

    @Test
    void validatesConstructionAndNullNotificationBeforeTransport() {
        assertThrows(NotificationConfigurationException.class, () -> new SlackConfig(null, URL));
        assertThrows(NotificationConfigurationException.class,
                () -> new SimulatedSlackSender(null, SlackSimulation.ACCEPTED));
        assertThrows(NotificationConfigurationException.class,
                () -> new SimulatedSlackSender(CONFIG, (SlackSimulation) null));
        assertThrows(NotificationConfigurationException.class,
                () -> new SimulatedSlackSender(CONFIG, (SlackClient) null));
        var sender = new SimulatedSlackSender(CONFIG, (url, request) -> { fail("No transport expected"); return null; });
        assertThrows(NotificationValidationException.class, () -> sender.send(null));
    }

    @Test
    void registersTwoDestinationsWithoutChangingTheCore() {
        var other = new ProviderId("slack-operations");
        var client = Notifications.builder()
                .register(SlackNotification.class, ID, new SimulatedSlackSender(CONFIG, SlackSimulation.ACCEPTED))
                .register(SlackNotification.class, other, new SimulatedSlackSender(
                        new SlackConfig(other, "https://hooks.slack.com/services/FAKE/OTHER/not-a-secret"),
                        SlackSimulation.ACCEPTED))
                .defaultProvider(SlackNotification.class, ID).build();
        assertEquals(ID, client.send(MESSAGE).providerId());
        assertEquals(other, client.send(MESSAGE, other).providerId());
        assertEquals(ID, client.send(MESSAGE).providerId());
    }
}
