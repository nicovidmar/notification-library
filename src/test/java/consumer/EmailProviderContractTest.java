package consumer;

import dev.challenge.notifications.adapters.mailgun.*;
import dev.challenge.notifications.adapters.sendgrid.*;
import dev.challenge.notifications.channels.email.*;
import dev.challenge.notifications.configuration.Notifications;
import dev.challenge.notifications.domain.*;
import dev.challenge.notifications.spi.NotificationSender;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.*;

/** The same observable contract runs against both production simulators, without HTTP. */
class EmailProviderContractTest {
    private static final EmailNotification EMAIL = new EmailNotification(
            new EmailAddress("recipient@example.com"), "Welcome", "Hello");
    private record Fixture(ProviderId id, NotificationSender<EmailNotification> accepted,
                           NotificationSender<EmailNotification> rejected) {
        @Override public String toString() { return id.value(); }
    }

    static Stream<Fixture> providers() {
        var from = new EmailAddress("sender@example.com");
        var sg = new SendGridConfig(new ProviderId("sendgrid"), "FAKE_KEY", from);
        var mg = new MailgunConfig(new ProviderId("mailgun"), "mg.example.com", "FAKE_KEY", from);
        return Stream.of(
                new Fixture(sg.providerId(), new SimulatedSendGridSender(sg, SendGridSimulation.ACCEPTED),
                        new SimulatedSendGridSender(sg, SendGridSimulation.UNAUTHORIZED)),
                new Fixture(mg.providerId(), new SimulatedMailgunSender(mg, MailgunSimulation.ACCEPTED),
                        new SimulatedMailgunSender(mg, MailgunSimulation.UNAUTHORIZED)));
    }

    @ParameterizedTest @MethodSource("providers")
    void acceptsTheSameNotificationThroughDefaultOrExplicitSelection(Fixture provider) {
        var client = Notifications.builder()
                .register(EmailNotification.class, provider.id(), provider.accepted())
                .defaultProvider(EmailNotification.class, provider.id()).build();
        var result = client.send(EMAIL);
        assertEquals(provider.id(), result.providerId());
        assertTrue(result.simulated());
        assertEquals(result, client.send(EMAIL, provider.id()));
        assertEquals("Welcome", EMAIL.subject());
        assertEquals("Hello", EMAIL.body());
        assertThrows(NotificationValidationException.class, () -> provider.accepted().send(null));
    }

    @ParameterizedTest @MethodSource("providers")
    void selectionPreservesDefaultAndDoesNotFallBackOnFailure(Fixture selected) {
        var other = providers().filter(p -> !p.id().equals(selected.id())).findFirst().orElseThrow();
        var otherCalls = new AtomicInteger();
        var selectedCalls = new AtomicInteger();
        var client = Notifications.builder()
                .register(EmailNotification.class, other.id(), email -> {
                    otherCalls.incrementAndGet(); return other.accepted().send(email);
                })
                .register(EmailNotification.class, selected.id(), email -> {
                    selectedCalls.incrementAndGet(); return selected.rejected().send(email);
                })
                .defaultProvider(EmailNotification.class, other.id()).build();

        var failure = assertThrows(NotificationDeliveryException.class, () -> client.send(EMAIL, selected.id()));
        assertEquals(FailureCategory.AUTHENTICATION, failure.category());
        assertEquals(selected.id(), failure.providerId());
        assertEquals(1, selectedCalls.get());
        assertEquals(0, otherCalls.get());
        assertEquals(other.id(), client.send(EMAIL).providerId());
        assertEquals(1, otherCalls.get());
    }

    @ParameterizedTest @MethodSource("providers")
    void eitherProviderCanBeDefaultWhileTheOtherIsSelectedPerSend(Fixture preferred) {
        var other = providers().filter(p -> !p.id().equals(preferred.id())).findFirst().orElseThrow();
        var client = Notifications.builder()
                .register(EmailNotification.class, other.id(), other.accepted())
                .register(EmailNotification.class, preferred.id(), preferred.accepted())
                .defaultProvider(EmailNotification.class, preferred.id()).build();
        assertEquals(preferred.id(), client.send(EMAIL).providerId());
        assertEquals(other.id(), client.send(EMAIL, other.id()).providerId());
        assertEquals(preferred.id(), client.send(EMAIL).providerId());
    }
}
