import dev.challenge.notifications.adapters.mailgun.*;
import dev.challenge.notifications.adapters.sendgrid.*;
import dev.challenge.notifications.channels.email.*;
import dev.challenge.notifications.configuration.Notifications;
import dev.challenge.notifications.domain.ProviderId;

/** One notification, two provider protocols; all attempts are simulated. */
public class MultiProviderEmailDemo {
    public static void main(String[] args) {
        var sendgrid = new ProviderId("sendgrid");
        var mailgun = new ProviderId("mailgun");
        var from = new EmailAddress("sender@example.com");
        var client = Notifications.builder()
                .register(EmailNotification.class, sendgrid, new SimulatedSendGridSender(
                        new SendGridConfig(sendgrid, "FAKE_SENDGRID_KEY", from), SendGridSimulation.ACCEPTED))
                .register(EmailNotification.class, mailgun, new SimulatedMailgunSender(
                        new MailgunConfig(mailgun, "mg.example.com", "FAKE_MAILGUN_KEY", from), MailgunSimulation.ACCEPTED))
                .defaultProvider(EmailNotification.class, sendgrid).build();

        var email = new EmailNotification(new EmailAddress("demo@example.com"), "Welcome", "Hello!");
        var defaultReceipt = client.send(email);
        var selectedReceipt = client.send(email, mailgun);
        var nextReceipt = client.send(email);
        if (!defaultReceipt.providerId().equals(sendgrid) || !selectedReceipt.providerId().equals(mailgun)
                || !nextReceipt.providerId().equals(sendgrid) || !selectedReceipt.simulated()
                || selectedReceipt.providerMessageId().isEmpty()) {
            throw new AssertionError("Provider selection contract failed");
        }
        System.out.println("Default: " + defaultReceipt.providerId());
        System.out.println("Selected: " + selectedReceipt.providerId() + ", simulated=" + selectedReceipt.simulated());
        System.out.println("Mailgun supplied a simulated ID: " + selectedReceipt.providerMessageId().isPresent());
        System.out.println("Default preserved: " + nextReceipt.providerId());
    }
}
