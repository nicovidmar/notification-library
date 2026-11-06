package dev.challenge.notifications.adapters.fcm;

import dev.challenge.notifications.channels.push.DeviceToken;
import dev.challenge.notifications.channels.push.PushNotification;
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

class FcmTest {
    private final ProviderId id = new ProviderId("fcm");
    private final FcmConfig config = new FcmConfig(id, "demo-project", () -> "FAKE_TOKEN");
    private final PushNotification message = new PushNotification(new DeviceToken("device"), "title", "body");

    @Test
    void mapsAndRoutes() {
        var calls = new AtomicInteger();
        var sender = new SimulatedFcmSender(config, (token, request) -> {
            calls.incrementAndGet();
            assertEquals("device", request.token());
            assertEquals("title", request.title());
            assertEquals("body", request.body());
            return new FcmResponse(200, "projects/demo-project/messages/123", null);
        });
        var client = Notifications.builder()
                .register(PushNotification.class, id, sender)
                .defaultProvider(PushNotification.class, id).build();
        var receipt = client.send(message);
        assertEquals(id, receipt.providerId());
        assertTrue(receipt.simulated());
        assertTrue(receipt.providerMessageId().isPresent());
        assertEquals(1, calls.get());
        assertFalse(message.toString().contains("body"));
        assertFalse(config.toString().contains("FAKE"));
    }

    @ParameterizedTest
    @EnumSource(FcmSimulation.class)
    void scenarios(FcmSimulation scenario) {
        var sender = new SimulatedFcmSender(config, scenario);
        if (scenario == FcmSimulation.ACCEPTED) {
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
            var sender = new SimulatedFcmSender(config, (token, request) ->
                    nullResponse ? null : new FcmResponse(200, "wrong", null));
            assertEquals(FailureCategory.INVALID_RESPONSE,
                    assertThrows(NotificationDeliveryException.class, () -> sender.send(message)).category());
        }
    }

    @Test
    void nullBeforeEffectsAndBugsPropagate() {
        var bug = new IllegalStateException("bug");
        var calls = new AtomicInteger();
        var sender = new SimulatedFcmSender(config, (token, request) -> {
            calls.incrementAndGet();
            throw bug;
        });
        assertThrows(NotificationValidationException.class, () -> sender.send(null));
        assertEquals(0, calls.get());
        assertSame(bug, assertThrows(IllegalStateException.class, () -> sender.send(message)));
    }

    @Test
    void transportTextIsNotExposed() {
        var sender = new SimulatedFcmSender(config, (token, request) -> {
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
                () -> new SimulatedFcmSender(null, FcmSimulation.ACCEPTED));
        assertThrows(NotificationConfigurationException.class,
                () -> new SimulatedFcmSender(config, (FcmSimulation) null));
    }

    @Test
    void categorizesForbiddenAsAuthenticationFailure() {
        var sender = new SimulatedFcmSender(config,
                (token, request) -> new FcmResponse(403, null, null));
        assertEquals(FailureCategory.AUTHENTICATION,
                assertThrows(NotificationDeliveryException.class, () -> sender.send(message)).category());
    }
}
