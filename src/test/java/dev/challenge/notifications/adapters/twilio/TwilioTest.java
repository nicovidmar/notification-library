package dev.challenge.notifications.adapters.twilio;

import dev.challenge.notifications.channels.sms.PhoneNumber;
import dev.challenge.notifications.channels.sms.SmsNotification;
import dev.challenge.notifications.configuration.Notifications;
import dev.challenge.notifications.domain.FailureCategory;
import dev.challenge.notifications.domain.NotificationConfigurationException;
import dev.challenge.notifications.domain.NotificationDeliveryException;
import dev.challenge.notifications.domain.NotificationValidationException;
import dev.challenge.notifications.domain.ProviderId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class TwilioTest {
    private final ProviderId id = new ProviderId("twilio");
    private final TwilioConfig config = new TwilioConfig(id, "AC_FAKE", "SK_FAKE", "FAKE_SECRET", new PhoneNumber("+5491112345678"));
    private final SmsNotification message = new SmsNotification(new PhoneNumber("+5491187654321"), "body");

    @Test
    void mapsAndRoutes() {
        var calls = new AtomicInteger();
        var sender = new SimulatedTwilioSender(config, (key, secret, request) -> {
            calls.incrementAndGet();
            assertEquals("+5491187654321", request.to());
            assertEquals("body", request.body());
            assertEquals("AC_FAKE", request.accountSid());
            return new TwilioResponse(201, "SM_FAKE", "queued");
        });
        var client = Notifications.builder()
                .register(SmsNotification.class, id, sender)
                .defaultProvider(SmsNotification.class, id).build();
        var receipt = client.send(message);
        assertEquals(id, receipt.providerId());
        assertTrue(receipt.simulated());
        assertTrue(receipt.providerMessageId().isPresent());
        assertEquals(1, calls.get());
        assertFalse(message.toString().contains("body"));
        assertFalse(config.toString().contains("FAKE"));
    }

    @ParameterizedTest
    @EnumSource(TwilioSimulation.class)
    void scenarios(TwilioSimulation scenario) {
        var sender = new SimulatedTwilioSender(config, scenario);
        if (scenario == TwilioSimulation.ACCEPTED) {
            assertTrue(sender.send(message).simulated());
            return;
        }
        var error = assertThrows(NotificationDeliveryException.class, () -> sender.send(message));
        var expected = switch (scenario) {
            case REJECTED -> FailureCategory.REJECTED;
            case UNAUTHORIZED -> FailureCategory.AUTHENTICATION;
            case RATE_LIMITED -> FailureCategory.RATE_LIMIT;
            case UNAVAILABLE -> FailureCategory.UNAVAILABLE;
            default -> FailureCategory.UNKNOWN_OUTCOME;
        };
        assertEquals(expected, error.category());
        assertNull(error.getCause());
    }

    @Test
    void rejectsMalformedAndNullResponses() {
        for (boolean nullResponse : new boolean[] {true, false}) {
            var sender = new SimulatedTwilioSender(config, (key, secret, request) ->
                    nullResponse ? null : new TwilioResponse(201, null, "queued"));
            assertEquals(FailureCategory.INVALID_RESPONSE,
                    assertThrows(NotificationDeliveryException.class, () -> sender.send(message)).category());
        }
    }

    @Test
    void nullBeforeEffectsAndBugsPropagate() {
        var bug = new IllegalStateException("bug");
        var calls = new AtomicInteger();
        var sender = new SimulatedTwilioSender(config, (key, secret, request) -> {
            calls.incrementAndGet();
            throw bug;
        });
        assertThrows(NotificationValidationException.class, () -> sender.send(null));
        assertEquals(0, calls.get());
        assertSame(bug, assertThrows(IllegalStateException.class, () -> sender.send(message)));
    }

    @Test
    void transportTextIsNotExposed() {
        var sender = new SimulatedTwilioSender(config, (key, secret, request) -> {
            throw new IOException("PRIVATE_SECRET");
        });
        var failure = assertThrows(NotificationDeliveryException.class, () -> sender.send(message));
        assertEquals(FailureCategory.UNKNOWN_OUTCOME, failure.category());
        assertNull(failure.getCause());
        assertFalse(failure.toString().contains("PRIVATE_SECRET"));
    }

    @Test
    void wiringMustExist() {
        assertThrows(NotificationConfigurationException.class,
                () -> new SimulatedTwilioSender(null, TwilioSimulation.ACCEPTED));
        assertThrows(NotificationConfigurationException.class,
                () -> new SimulatedTwilioSender(config, (TwilioSimulation) null));
    }

    @Test
    void rejectsSuccessfulStatusWithUnexpectedState() {
        var sender = new SimulatedTwilioSender(config,
                (key, secret, request) -> new TwilioResponse(201, "SM_FAKE", "failed"));
        assertEquals(FailureCategory.INVALID_RESPONSE,
                assertThrows(NotificationDeliveryException.class, () -> sender.send(message)).category());
    }

    @Test
    void categorizesForbiddenAsAuthenticationFailure() {
        var sender = new SimulatedTwilioSender(config,
                (key, secret, request) -> new TwilioResponse(403, null, null));
        assertEquals(FailureCategory.AUTHENTICATION,
                assertThrows(NotificationDeliveryException.class, () -> sender.send(message)).category());
    }
}
