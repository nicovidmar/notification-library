package consumer;

import dev.challenge.notifications.adapters.fcm.FcmConfig;
import dev.challenge.notifications.adapters.fcm.FcmSimulation;
import dev.challenge.notifications.adapters.fcm.SimulatedFcmSender;
import dev.challenge.notifications.channels.push.DeviceToken;
import dev.challenge.notifications.channels.push.PushNotification;
import dev.challenge.notifications.channels.sms.PhoneNumber;
import dev.challenge.notifications.channels.sms.SmsNotification;
import dev.challenge.notifications.domain.NotificationConfigurationException;
import dev.challenge.notifications.domain.NotificationValidationException;
import dev.challenge.notifications.domain.ProviderId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class MobileValidationTest {
    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "123", "+0", "+1", "+1234567890123456", "+12a"})
    void invalidPhones(String value) {
        assertThrows(NotificationValidationException.class, () -> new PhoneNumber(value));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\n"})
    void blankFields(String value) {
        assertThrows(NotificationValidationException.class, () -> new DeviceToken(value));
        assertThrows(NotificationValidationException.class,
                () -> new SmsNotification(new PhoneNumber("+12"), value));
        assertThrows(NotificationValidationException.class,
                () -> new PushNotification(new DeviceToken("opaque"), value, "body"));
        assertThrows(NotificationValidationException.class,
                () -> new PushNotification(new DeviceToken("opaque"), "title", value));
    }

    @Test
    void allowsLongSmsAndOpaqueTokens() {
        assertEquals(300, new SmsNotification(new PhoneNumber("+123456789012345"), "x".repeat(300)).body().length());
        assertEquals("any opaque token", new DeviceToken("any opaque token").value());
        assertThrows(NotificationValidationException.class, () -> new SmsNotification(null, "body"));
        assertThrows(NotificationValidationException.class, () -> new PushNotification(null, "title", "body"));
    }

    @Test
    void obtainsTokenPerAttemptAndRejectsBlankTokens() {
        var calls = new AtomicInteger();
        var sender = new SimulatedFcmSender(new FcmConfig(new ProviderId("fcm"), "demo", () -> {
            calls.incrementAndGet();
            return "fake";
        }), FcmSimulation.ACCEPTED);
        var message = new PushNotification(new DeviceToken("device"), "title", "body");
        sender.send(message);
        sender.send(message);
        assertEquals(2, calls.get());
        var invalid = new SimulatedFcmSender(
                new FcmConfig(new ProviderId("fcm"), "demo", () -> " "), FcmSimulation.ACCEPTED);
        assertThrows(NotificationConfigurationException.class, () -> invalid.send(message));
    }
}
