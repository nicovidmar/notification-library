import dev.challenge.notifications.adapters.sendgrid.SendGridConfig;
import dev.challenge.notifications.adapters.sendgrid.SendGridSimulation;
import dev.challenge.notifications.adapters.sendgrid.SimulatedSendGridSender;
import dev.challenge.notifications.channels.email.EmailAddress;
import dev.challenge.notifications.channels.email.EmailNotification;
import dev.challenge.notifications.configuration.Notifications;
import dev.challenge.notifications.domain.NotificationDeliveryException;
import dev.challenge.notifications.domain.NotificationValidationException;
import dev.challenge.notifications.domain.ProviderId;

/** Run with Java 21 source-file mode against the packaged library; no real credentials. */
public class EmailDemo {
    public static void main(String[] args) {
        var primary = new ProviderId("sendgrid");
        var restricted = new ProviderId("sendgrid-restricted");
        var from = new EmailAddress("sender@example.com");
        var client = Notifications.builder()
                .register(EmailNotification.class, primary, new SimulatedSendGridSender(
                        new SendGridConfig(primary, "FAKE_DEMO_KEY", from), SendGridSimulation.ACCEPTED))
                .register(EmailNotification.class, restricted, new SimulatedSendGridSender(
                        new SendGridConfig(restricted, "FAKE_RESTRICTED_KEY", from), SendGridSimulation.UNAUTHORIZED))
                .defaultProvider(EmailNotification.class, primary)
                .build();

        var email = new EmailNotification(new EmailAddress("demo@example.com"), "Welcome", "Hello!");
        var receipt = client.send(email);
        System.out.println("Accepted: provider=" + receipt.providerId() + ", simulated=" + receipt.simulated());

        try {
            client.send(email, restricted);
            throw new AssertionError("The restricted scenario must reject the attempt");
        } catch (NotificationDeliveryException failure) {
            System.out.println("Expected provider failure: " + failure.category());
        }

        try {
            client.send(new EmailNotification(new EmailAddress("invalid"), "Welcome", "Hello!"));
            throw new AssertionError("An invalid address must be rejected before sending");
        } catch (NotificationValidationException failure) {
            System.out.println("Expected validation failure: " + failure.field() + "/" + failure.code());
        }

        var afterOverride = client.send(email);
        if (!afterOverride.providerId().equals(primary)) {
            throw new AssertionError("Selecting a provider must not change the default");
        }
        System.out.println("Default preserved: " + afterOverride.providerId());
    }
}
