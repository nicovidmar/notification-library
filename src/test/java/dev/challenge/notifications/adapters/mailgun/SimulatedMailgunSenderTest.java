package dev.challenge.notifications.adapters.mailgun;

import dev.challenge.notifications.channels.email.EmailAddress;
import dev.challenge.notifications.channels.email.EmailNotification;
import dev.challenge.notifications.domain.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

class SimulatedMailgunSenderTest {
    private final ProviderId id = new ProviderId("mailgun");
    private final EmailAddress from = new EmailAddress("sender@example.com");
    private final MailgunConfig config = new MailgunConfig(id, "mg.example.com", "FAKE_PRIVATE_KEY", from);
    private final EmailNotification email = new EmailNotification(
            new EmailAddress("recipient@example.com"), "PRIVATE_SUBJECT", "PRIVATE_BODY");

    @Test
    void mapsMailgunFieldsAndReturnsTheOpaqueRemoteId() {
        var captured = new AtomicReference<MailgunRequest>();
        var calls = new AtomicInteger();
        var sender = new SimulatedMailgunSender(config, (username, key, request) -> {
            calls.incrementAndGet();
            assertEquals("api", username);
            assertEquals("FAKE_PRIVATE_KEY", key);
            captured.set(request);
            return new MailgunResponse(200, "<opaque@example.com>", "Any nonblank acceptance message");
        });
        var receipt = sender.send(email);
        assertEquals(id, receipt.providerId());
        assertTrue(receipt.simulated());
        assertEquals("<opaque@example.com>", receipt.providerMessageId().orElseThrow());
        assertEquals(1, calls.get());
        assertEquals("mg.example.com", captured.get().domain());
        assertEquals("sender@example.com", captured.get().from());
        assertEquals("recipient@example.com", captured.get().to());
        assertEquals("PRIVATE_SUBJECT", captured.get().subject());
        assertEquals("PRIVATE_BODY", captured.get().text());
        assertFalse(captured.get().toString().contains("PRIVATE"));
        assertFalse(captured.get().toString().contains("example.com"));
        assertFalse(receipt.toString().contains("opaque@example.com"));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\n", "\t"})
    void rejectsMissingSuccessFields(String value) {
        for (var response : java.util.List.of(
                new MailgunResponse(200, value, "queued"),
                new MailgunResponse(200, "id", value))) {
            var sender = new SimulatedMailgunSender(config, (user, key, request) -> response);
            var failure = assertThrows(NotificationDeliveryException.class, () -> sender.send(email));
            assertEquals(FailureCategory.INVALID_RESPONSE, failure.category());
            assertEquals(id, failure.providerId());
        }
    }

    @ParameterizedTest
    @CsvSource({"400,REJECTED", "401,AUTHENTICATION", "403,AUTHENTICATION", "404,REJECTED",
            "429,RATE_LIMIT", "500,UNAVAILABLE", "503,UNAVAILABLE", "202,INVALID_RESPONSE",
            "201,INVALID_RESPONSE", "204,INVALID_RESPONSE", "302,INVALID_RESPONSE", "0,INVALID_RESPONSE", "600,INVALID_RESPONSE"})
    void interpretsStatusesWithoutRetryOrExposingTheResponse(int status, FailureCategory category) {
        var calls = new AtomicInteger();
        var response = new MailgunResponse(status, "PRIVATE_ID", "PRIVATE_MESSAGE FAKE_PRIVATE_KEY");
        var sender = new SimulatedMailgunSender(config, (user, key, request) -> {
            calls.incrementAndGet();
            return response;
        });
        var failure = assertThrows(NotificationDeliveryException.class, () -> sender.send(email));
        assertEquals(category, failure.category());
        assertEquals(1, calls.get());
        assertFalse(failure.toString().contains("PRIVATE"));
        assertNull(failure.getCause());
        assertFalse(response.toString().contains("PRIVATE"));
    }

    @Test
    void nullResponseAndTransportFailureAreSafeTypedFailures() {
        var empty = new SimulatedMailgunSender(config, (user, key, request) -> null);
        assertEquals(FailureCategory.INVALID_RESPONSE,
                assertThrows(NotificationDeliveryException.class, () -> empty.send(email)).category());
        var failing = new SimulatedMailgunSender(config, (user, key, request) -> {
            throw new IOException("FAKE_PRIVATE_KEY recipient@example.com PRIVATE_BODY");
        });
        var failure = assertThrows(NotificationDeliveryException.class, () -> failing.send(email));
        assertEquals(FailureCategory.UNKNOWN_OUTCOME, failure.category());
        assertFalse(failure.toString().contains("PRIVATE"));
        assertFalse(failure.toString().contains("example.com"));
        assertNull(failure.getCause());
        assertEquals(0, failure.getSuppressed().length);
    }

    @Test
    void rejectsNullBeforeEffectsAndPropagatesProgrammingBugs() {
        var calls = new AtomicInteger();
        var bug = new IllegalStateException("programmer error");
        var sender = new SimulatedMailgunSender(config, (user, key, request) -> {
            calls.incrementAndGet(); throw bug;
        });
        assertThrows(NotificationValidationException.class, () -> sender.send(null));
        assertEquals(0, calls.get());
        assertSame(bug, assertThrows(IllegalStateException.class, () -> sender.send(email)));
        assertEquals(1, calls.get());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "localhost", "https://mg.example.com", "mg.example.com/path",
            "mg.example.com:443", "-mg.example.com", "mg-.example.com", "mg..example.com",
            "mg.example.com.", "mg.exámple.com", "mg.example.com\n"})
    void rejectsDomainsOutsideTheSupportedSubset(String domain) {
        var failure = assertThrows(NotificationConfigurationException.class,
                () -> new MailgunConfig(id, domain, "fake", from));
        assertEquals("INVALID_MAILGUN_CONFIG", failure.code());
        assertFalse(failure.toString().contains("example.com"));
    }

    @Test
    void validatesDomainBoundariesWithoutNormalizingOrInferringSenderDomain() {
        assertEquals("MG.Example.com", new MailgunConfig(id, "MG.Example.com", "fake", from).domain());
        String max = "a".repeat(63) + "." + "b".repeat(63) + "." + "c".repeat(63) + "." + "d".repeat(61);
        assertEquals(253, new MailgunConfig(id, max, "fake", from).domain().length());
        assertThrows(NotificationConfigurationException.class, () -> new MailgunConfig(id, max + "d", "fake", from));
        assertThrows(NotificationConfigurationException.class,
                () -> new MailgunConfig(id, "a".repeat(64) + ".com", "fake", from));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "key\r", "key\n"})
    void rejectsUnsafeCredentials(String key) {
        assertThrows(NotificationConfigurationException.class, () -> new MailgunConfig(id, "mg.example.com", key, from));
    }

    @Test
    void validatesWiringAndRedactsConfiguration() {
        assertThrows(NotificationConfigurationException.class, () -> new MailgunConfig(null, "mg.example.com", "fake", from));
        assertThrows(NotificationConfigurationException.class, () -> new MailgunConfig(id, "mg.example.com", "fake", null));
        assertThrows(NotificationConfigurationException.class, () -> new SimulatedMailgunSender(null, MailgunSimulation.ACCEPTED));
        assertThrows(NotificationConfigurationException.class, () -> new SimulatedMailgunSender(config, (MailgunSimulation) null));
        assertThrows(NotificationConfigurationException.class, () -> new SimulatedMailgunSender(config, (MailgunClient) null));
        assertFalse(config.toString().contains("PRIVATE"));
        assertFalse(config.toString().contains("example.com"));
    }

    @Test
    void publicScenariosAreLocalAndRepeatable() {
        var sender = new SimulatedMailgunSender(config, MailgunSimulation.ACCEPTED);
        assertEquals(sender.send(email), sender.send(email));
        for (var scenario : MailgunSimulation.values()) {
            if (scenario != MailgunSimulation.ACCEPTED) {
                assertThrows(NotificationDeliveryException.class,
                        () -> new SimulatedMailgunSender(config, scenario).send(email));
            }
        }
    }
}
