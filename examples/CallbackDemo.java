import dev.challenge.notifications.adapters.sendgrid.*;
import dev.challenge.notifications.api.AsyncNotificationClient;
import dev.challenge.notifications.api.ObservedNotificationClient;
import dev.challenge.notifications.channels.email.*;
import dev.challenge.notifications.configuration.Notifications;
import dev.challenge.notifications.domain.*;

import java.util.concurrent.atomic.AtomicInteger;

/** The application observes outcomes; the library performs no logging or event scheduling. */
public class CallbackDemo {
    public static void main(String[] args) {
        var id = new ProviderId("sendgrid");
        var sync = Notifications.builder()
                .register(EmailNotification.class, id, new SimulatedSendGridSender(
                        new SendGridConfig(id, "FAKE_KEY", new EmailAddress("sender@example.com")),
                        SendGridSimulation.ACCEPTED))
                .defaultProvider(EmailNotification.class, id).build();
        var accepted = new AtomicInteger();
        var observed = new ObservedNotificationClient(sync, event -> {
            if (event instanceof NotificationEvent.Accepted) accepted.incrementAndGet();
        });
        var email = new EmailNotification(new EmailAddress("demo@example.com"), "Welcome", "Hello");
        try (var async = AsyncNotificationClient.create(observed)) {
            async.sendAsync(email).join();
        }
        if (accepted.get() != 1) {
            throw new AssertionError("Expected one acceptance event");
        }

        // A defective observer cannot turn an accepted send into an ordinary failure.
        var defectiveObserver = new ObservedNotificationClient(sync, event -> {
            throw new IllegalStateException("Simulated observer failure");
        });
        if (!defectiveObserver.send(email).simulated()) {
            throw new AssertionError("Expected simulated acceptance");
        }
        System.out.println("Callback observed one acceptance; observer failure preserved the send result.");
    }
}
