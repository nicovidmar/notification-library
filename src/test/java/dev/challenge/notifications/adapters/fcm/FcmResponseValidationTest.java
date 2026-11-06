package dev.challenge.notifications.adapters.fcm;

import dev.challenge.notifications.channels.push.DeviceToken;
import dev.challenge.notifications.channels.push.PushNotification;
import dev.challenge.notifications.domain.FailureCategory;
import dev.challenge.notifications.domain.NotificationDeliveryException;
import dev.challenge.notifications.domain.ProviderId;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FcmResponseValidationTest {
    private final FcmConfig config = new FcmConfig(new ProviderId("fcm"), "demo", () -> "fake");
    private final PushNotification message = new PushNotification(new DeviceToken("device"), "title", "body");

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"projects/other/messages/id", "projects/demo/messages/", "projects/demo/messages/a/b",
            "projects/demo/messages/a b", "projects/demo/messages/a\tb", "projects/demo/messages/a\nb"})
    void rejectsInvalidNames(String name) {
        var sender = new SimulatedFcmSender(config, (token, request) -> new FcmResponse(200, name, null));
        assertEquals(FailureCategory.INVALID_RESPONSE,
                assertThrows(NotificationDeliveryException.class, () -> sender.send(message)).category());
    }

    @Test
    void rejectsContradictorySuccessAndError() {
        var sender = new SimulatedFcmSender(config, (token, request) ->
                new FcmResponse(200, "projects/demo/messages/id", "UNREGISTERED"));
        assertEquals(FailureCategory.INVALID_RESPONSE,
                assertThrows(NotificationDeliveryException.class, () -> sender.send(message)).category());
    }
}
