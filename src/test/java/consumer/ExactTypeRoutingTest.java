package consumer;

import dev.challenge.notifications.configuration.Notifications;
import dev.challenge.notifications.domain.Notification;
import dev.challenge.notifications.domain.NotificationConfigurationException;
import dev.challenge.notifications.domain.ProviderId;
import dev.challenge.notifications.domain.SendReceipt;
import org.junit.jupiter.api.Test;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class ExactTypeRoutingTest {
    private static class BaseNotification implements Notification { }
    private static final class DerivedNotification extends BaseNotification { }

    @Test
    void registrationDoesNotImplicitlyMatchSubclass() {
        var id = new ProviderId("base");
        var calls = new AtomicInteger();
        var client = Notifications.builder()
                .register(BaseNotification.class, id, message -> {
                    calls.incrementAndGet();
                    return new SendReceipt(id, Optional.empty(), true);
                })
                .defaultProvider(BaseNotification.class, id).build();

        assertEquals(id, client.send(new BaseNotification()).providerId());
        var failure = assertThrows(NotificationConfigurationException.class,
                () -> client.send(new DerivedNotification()));
        assertEquals("UNREGISTERED_CHANNEL", failure.code());
        assertThrows(NotificationConfigurationException.class,
                () -> client.send(new DerivedNotification(), id));
        assertEquals(1, calls.get());
    }
}
