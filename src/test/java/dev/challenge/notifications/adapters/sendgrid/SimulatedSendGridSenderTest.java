package dev.challenge.notifications.adapters.sendgrid;

import dev.challenge.notifications.channels.email.*;
import dev.challenge.notifications.configuration.Notifications;
import dev.challenge.notifications.domain.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class SimulatedSendGridSenderTest {
    private final ProviderId provider = new ProviderId("sendgrid");
    private final SendGridConfig config = new SendGridConfig(provider, "FAKE_PRIVATE_KEY",
            new EmailAddress("sender@example.com"));
    private final EmailNotification email = new EmailNotification(new EmailAddress("recipient@example.com"),
            "PRIVATE_SUBJECT", "PRIVATE_BODY");

    @Test
    void mapsTheDocumentedRequestAndAccepts202WithoutBodyOrMessageId() {
        var captured = new AtomicReference<SendGridRequest>();
        var calls = new AtomicInteger();
        var sender = new SimulatedSendGridSender(config, (key, request) -> {
            calls.incrementAndGet();
            assertEquals("FAKE_PRIVATE_KEY", key);
            captured.set(request);
            return new SendGridResponse(202);
        });
        var client = Notifications.builder().register(EmailNotification.class, provider, sender)
                .defaultProvider(EmailNotification.class, provider).build();
        var receipt = client.send(email);
        assertEquals(provider, receipt.providerId());
        assertTrue(receipt.simulated());
        assertTrue(receipt.providerMessageId().isEmpty());
        assertEquals(1, calls.get());
        var request = captured.get();
        assertEquals("sender@example.com", request.from().email());
        assertEquals("recipient@example.com", request.personalizations().getFirst().to().getFirst().email());
        assertEquals("PRIVATE_SUBJECT", request.subject());
        assertEquals("text/plain", request.content().getFirst().type());
        assertEquals("PRIVATE_BODY", request.content().getFirst().value());
        assertThrows(UnsupportedOperationException.class, () -> request.content().clear());
        assertThrows(UnsupportedOperationException.class, () -> request.personalizations().getFirst().to().clear());
        assertFalse(request.toString().contains("PRIVATE"));
        assertFalse(request.from().toString().contains("sender@example.com"));
        assertFalse(request.personalizations().toString().contains("recipient@example.com"));
        assertFalse(request.content().toString().contains("PRIVATE"));
    }

    @ParameterizedTest
    @CsvSource({"400,REJECTED", "401,AUTHENTICATION", "403,AUTHENTICATION", "413,REJECTED",
            "429,RATE_LIMIT", "500,UNAVAILABLE", "503,UNAVAILABLE",
            "200,INVALID_RESPONSE", "201,INVALID_RESPONSE", "204,INVALID_RESPONSE",
            "302,INVALID_RESPONSE", "0,INVALID_RESPONSE", "600,INVALID_RESPONSE"})
    void classifiesHttpOutcomesWithoutFalseSuccess(int status, FailureCategory expected) {
        var calls = new AtomicInteger();
        var sender = new SimulatedSendGridSender(config, (key, request) -> {
            calls.incrementAndGet();
            return new SendGridResponse(status);
        });
        var failure = assertThrows(NotificationDeliveryException.class, () -> sender.send(email));
        assertEquals(expected, failure.category());
        assertEquals(provider, failure.providerId());
        assertEquals(1, calls.get());
        assertNull(failure.getCause());
    }

    @Test
    void handlesNullResponsesAndUnknownTransportOutcomeSafely() {
        var nullSender = new SimulatedSendGridSender(config, (key, request) -> null);
        assertEquals(FailureCategory.INVALID_RESPONSE,
                assertThrows(NotificationDeliveryException.class, () -> nullSender.send(email)).category());
        var sender = new SimulatedSendGridSender(config, (key, request) -> {
            throw new IOException("FAKE_PRIVATE_KEY PRIVATE_BODY recipient@example.com");
        });
        var failure = assertThrows(NotificationDeliveryException.class, () -> sender.send(email));
        assertEquals(FailureCategory.UNKNOWN_OUTCOME, failure.category());
        assertFalse(failure.toString().contains("PRIVATE"));
        assertFalse(failure.toString().contains("recipient@example.com"));
        assertNull(failure.getCause());
        assertEquals(0, failure.getSuppressed().length);
    }

    @Test
    void doesNotHideProgrammingErrorsAndRejectsNullBeforeInvokingTransport() {
        var calls = new AtomicInteger();
        var bug = new IllegalStateException("bug");
        var sender = new SimulatedSendGridSender(config, (key, request) -> { calls.incrementAndGet(); throw bug; });
        assertThrows(NotificationValidationException.class, () -> sender.send(null));
        assertEquals(0, calls.get());
        assertSame(bug, assertThrows(IllegalStateException.class, () -> sender.send(email)));
        assertEquals(1, calls.get());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "key\n", "key\r"})
    void rejectsMissingOrUnsafeCredentials(String key) {
        var failure = assertThrows(NotificationConfigurationException.class,
                () -> new SendGridConfig(provider, key, new EmailAddress("sender@example.com")));
        assertEquals("INVALID_SENDGRID_CONFIG", failure.code());
    }

    @Test
    void validatesConfigurationAndRedactsDiagnosticStrings() {
        assertThrows(NotificationConfigurationException.class, () -> new SendGridConfig(null, "fake", email.to()));
        assertThrows(NotificationConfigurationException.class, () -> new SendGridConfig(provider, "fake", null));
        assertThrows(NotificationConfigurationException.class,
                () -> new SimulatedSendGridSender(null, SendGridSimulation.ACCEPTED));
        assertThrows(NotificationConfigurationException.class,
                () -> new SimulatedSendGridSender(config, (SendGridSimulation) null));
        assertFalse(config.toString().contains("PRIVATE"));
        assertFalse(config.toString().contains("sender@example.com"));
    }

    @Test
    void publicSimulationsAreDeterministicAndExplicitlySimulated() {
        var sender = new SimulatedSendGridSender(config, SendGridSimulation.ACCEPTED);
        assertEquals(sender.send(email), sender.send(email));
        assertTrue(sender.send(email).simulated());
        for (var scenario : List.of(SendGridSimulation.REJECTED, SendGridSimulation.UNAUTHORIZED,
                SendGridSimulation.FORBIDDEN, SendGridSimulation.RATE_LIMITED,
                SendGridSimulation.UNAVAILABLE, SendGridSimulation.UNKNOWN_OUTCOME)) {
            assertThrows(NotificationDeliveryException.class,
                    () -> new SimulatedSendGridSender(config, scenario).send(email));
        }
    }
}
