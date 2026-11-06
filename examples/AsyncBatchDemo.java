import dev.challenge.notifications.adapters.sendgrid.*;
import dev.challenge.notifications.api.AsyncNotificationClient;
import dev.challenge.notifications.channels.email.*;
import dev.challenge.notifications.configuration.Notifications;
import dev.challenge.notifications.domain.*;
import java.util.Arrays;

/** Future composition and partial failures using only Java; no external service. */
public class AsyncBatchDemo {
    public static void main(String[] args) {
        var id = new ProviderId("sendgrid");
        var sync = Notifications.builder()
                .register(EmailNotification.class, id, new SimulatedSendGridSender(
                        new SendGridConfig(id, "FAKE_KEY", new EmailAddress("sender@example.com")),
                        SendGridSimulation.ACCEPTED))
                .defaultProvider(EmailNotification.class, id).build();
        var email = new EmailNotification(new EmailAddress("recipient@example.com"), "Welcome", "Hello");
        try (var async = AsyncNotificationClient.create(sync)) {
            var receipt = async.sendAsync(email, id).join();
            var results = async.sendBatchAsync(Arrays.asList(email, null, email)).join();
            if (!receipt.simulated() || !(results.get(0) instanceof BatchResult.Accepted)
                    || !(results.get(1) instanceof BatchResult.Failed)
                    || !(results.get(2) instanceof BatchResult.Accepted)) {
                throw new AssertionError("Async batch contract failed");
            }
            System.out.println("Async batch: accepted, failed, accepted (input order preserved)");
        }
    }
}
