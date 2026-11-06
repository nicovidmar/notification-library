import dev.challenge.notifications.adapters.sendgrid.*;
import dev.challenge.notifications.api.AsyncNotificationClient;
import dev.challenge.notifications.api.ObservedNotificationClient;
import dev.challenge.notifications.channels.email.*;
import dev.challenge.notifications.configuration.Notifications;
import dev.challenge.notifications.domain.*;
import dev.challenge.notifications.retry.RetryingSender;
import dev.challenge.notifications.spi.NotificationSender;

import java.util.concurrent.atomic.AtomicInteger;

/** A programmed transient failure demonstrates retry without network or timing dependencies. */
public class RetryDemo {
    public static void main(String[] args) {
        var id = new ProviderId("sendgrid");
        var provider = new SimulatedSendGridSender(
                new SendGridConfig(id, "FAKE_KEY", new EmailAddress("sender@example.com")),
                SendGridSimulation.ACCEPTED);
        var attempts = new AtomicInteger();
        NotificationSender<EmailNotification> transientFailure = email -> {
            if (attempts.incrementAndGet() == 1) {
                throw new NotificationDeliveryException(id, FailureCategory.UNAVAILABLE);
            }
            return provider.send(email);
        };
        var sync = Notifications.builder()
                .register(EmailNotification.class, id, new RetryingSender<>(transientFailure, 2))
                .defaultProvider(EmailNotification.class, id).build();
        var acceptedEvents = new AtomicInteger();
        var observed = new ObservedNotificationClient(sync, event -> {
            if (event instanceof NotificationEvent.Accepted) acceptedEvents.incrementAndGet();
        });
        try (var async = AsyncNotificationClient.create(observed)) {
            var receipt = async.sendAsync(new EmailNotification(
                    new EmailAddress("recipient@example.com"), "Welcome", "Hello")).join();
            if (!receipt.simulated() || attempts.get() != 2 || acceptedEvents.get() != 1) {
                throw new AssertionError("Expected two attempts and one final acceptance event");
            }
        }
        System.out.println("Retry: UNAVAILABLE then accepted; two attempts, one final event (simulated).");
    }
}
