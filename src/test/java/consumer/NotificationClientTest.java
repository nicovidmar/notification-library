package consumer;

import dev.challenge.notifications.api.NotificationClient;
import dev.challenge.notifications.configuration.Notifications;
import dev.challenge.notifications.domain.*;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class NotificationClientTest {
    // These types live outside the library and prove that registration is open.
    private record CustomNotification(String body) implements Notification { }
    private record AnotherNotification() implements Notification { }
    private final ProviderId first = new ProviderId("first");
    private final ProviderId second = new ProviderId("second");
    private final CustomNotification message = new CustomNotification("hello");

    private SendReceipt receipt(ProviderId provider) {
        return new SendReceipt(provider, Optional.empty(), true);
    }

    private NotificationClient client(dev.challenge.notifications.spi.NotificationSender<CustomNotification> sender) {
        return Notifications.builder().register(CustomNotification.class, first, sender)
                .defaultProvider(CustomNotification.class, first).build();
    }

    @Test
    void routesDefaultAndOverrideWithoutChangingDefault() {
        var firstCalls = new AtomicInteger();
        var secondCalls = new AtomicInteger();
        var client = Notifications.builder()
                .defaultProvider(CustomNotification.class, first)
                .register(CustomNotification.class, second, n -> { secondCalls.incrementAndGet(); return receipt(second); })
                .register(CustomNotification.class, first, n -> { firstCalls.incrementAndGet(); return receipt(first); })
                .build();
        assertEquals(first, client.send(message).providerId());
        assertEquals(second, client.send(message, second).providerId());
        assertEquals(first, client.send(message).providerId());
        assertEquals(2, firstCalls.get());
        assertEquals(1, secondCalls.get());
    }

    @Test
    void rejectsUnknownRoutesWithoutCallingAnySender() {
        var calls = new AtomicInteger();
        var client = client(n -> { calls.incrementAndGet(); return receipt(first); });
        assertThrows(NotificationConfigurationException.class, () -> client.send(message, second));
        assertThrows(NotificationConfigurationException.class, () -> client.send(new AnotherNotification()));
        assertThrows(NotificationConfigurationException.class, () -> client.send(message, null));
        assertThrows(NotificationValidationException.class, () -> client.send(null));
        assertThrows(NotificationValidationException.class, () -> client.send(null, first));
        assertEquals(0, calls.get());
    }

    @Test
    void providerIdsAreScopedToTheExactNotificationType() {
        var client = Notifications.builder()
                .register(CustomNotification.class, first, n -> receipt(first))
                .defaultProvider(CustomNotification.class, first)
                .register(AnotherNotification.class, first, n -> receipt(first))
                .register(AnotherNotification.class, second, n -> receipt(second))
                .defaultProvider(AnotherNotification.class, second).build();
        assertEquals(first, client.send(message).providerId());
        assertEquals(second, client.send(new AnotherNotification()).providerId());
        assertThrows(NotificationConfigurationException.class, () -> client.send(message, second));
    }

    @Test
    void validatesRegistrationsAndDefaultsBeforeSending() {
        assertThrows(NotificationConfigurationException.class, () -> Notifications.builder().build());
        assertThrows(NotificationConfigurationException.class, () -> Notifications.builder()
                .register(CustomNotification.class, first, n -> receipt(first)).build());
        assertThrows(NotificationConfigurationException.class, () -> Notifications.builder()
                .register(CustomNotification.class, first, n -> receipt(first))
                .defaultProvider(CustomNotification.class, second).build());
        assertThrows(NotificationConfigurationException.class, () -> Notifications.builder()
                .defaultProvider(CustomNotification.class, first).build());

        var builder = Notifications.builder().register(CustomNotification.class, first, n -> receipt(first))
                .defaultProvider(CustomNotification.class, first);
        assertThrows(NotificationConfigurationException.class,
                () -> builder.register(CustomNotification.class, first, n -> receipt(first)));
        assertThrows(NotificationConfigurationException.class,
                () -> builder.defaultProvider(CustomNotification.class, second));
        assertEquals(first, builder.build().send(message).providerId());
    }

    @Test
    void rejectsNullConfigurationWithTypedErrors() {
        assertThrows(NotificationConfigurationException.class,
                () -> Notifications.builder().register(null, first, n -> receipt(first)));
        assertThrows(NotificationConfigurationException.class,
                () -> Notifications.builder().register(CustomNotification.class, null, n -> receipt(first)));
        assertThrows(NotificationConfigurationException.class,
                () -> Notifications.builder().register(CustomNotification.class, first, null));
        assertThrows(NotificationConfigurationException.class,
                () -> Notifications.builder().defaultProvider(null, first));
        assertThrows(NotificationConfigurationException.class,
                () -> Notifications.builder().defaultProvider(CustomNotification.class, null));
    }

    @Test
    void buildingCreatesAnIndependentSnapshot() {
        var builder = Notifications.builder().register(CustomNotification.class, first, n -> receipt(first))
                .defaultProvider(CustomNotification.class, first);
        var original = builder.build();
        builder.register(CustomNotification.class, second, n -> receipt(second));
        assertThrows(NotificationConfigurationException.class, () -> original.send(message, second));
        assertEquals(second, builder.build().send(message, second).providerId());
        assertEquals(first, original.send(message).providerId());
    }

    @Test
    void rejectsNullAndMismatchedReceipts() {
        for (var sender : java.util.List.<dev.challenge.notifications.spi.NotificationSender<CustomNotification>>of(
                n -> null, n -> receipt(second))) {
            var failure = assertThrows(NotificationDeliveryException.class, () -> client(sender).send(message));
            assertEquals(FailureCategory.INVALID_RESPONSE, failure.category());
            assertEquals(first, failure.providerId());
        }
    }

    @Test
    void propagatesExpectedFailuresAndBugsWithoutRetryingOrWrapping() {
        var calls = new AtomicInteger();
        var rejection = new NotificationDeliveryException(first, FailureCategory.REJECTED);
        var failing = client(n -> { calls.incrementAndGet(); throw rejection; });
        assertSame(rejection, assertThrows(NotificationDeliveryException.class, () -> failing.send(message)));
        assertEquals(1, calls.get());

        var bug = new IllegalStateException("programmer error");
        assertSame(bug, assertThrows(IllegalStateException.class,
                () -> client(n -> { throw bug; }).send(message)));
    }
}
