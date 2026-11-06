package dev.challenge.notifications.adapters.twilio;

import dev.challenge.notifications.channels.sms.PhoneNumber;
import dev.challenge.notifications.domain.NotificationConfigurationException;
import dev.challenge.notifications.domain.ProviderId;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class TwilioCredentialsTest {
    @ParameterizedTest
    @ValueSource(strings = {"private\rvalue", "private\nvalue"})
    void rejectsHeaderBreaksInEveryCredential(String value) {
        var id = new ProviderId("twilio");
        var from = new PhoneNumber("+123456789");
        assertThrows(NotificationConfigurationException.class,
                () -> new TwilioConfig(id, value, "key", "secret", from));
        assertThrows(NotificationConfigurationException.class,
                () -> new TwilioConfig(id, "account", value, "secret", from));
        var failure = assertThrows(NotificationConfigurationException.class,
                () -> new TwilioConfig(id, "account", "key", value, from));
        assertFalse(failure.toString().contains("private"));
    }
}
