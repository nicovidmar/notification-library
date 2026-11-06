import dev.challenge.notifications.adapters.sendgrid.SendGridConfig;
import dev.challenge.notifications.adapters.sendgrid.SendGridSimulation;
import dev.challenge.notifications.adapters.sendgrid.SimulatedSendGridSender;
import dev.challenge.notifications.channels.email.EmailAddress;
import dev.challenge.notifications.channels.email.EmailNotification;
import dev.challenge.notifications.configuration.Notifications;
import dev.challenge.notifications.domain.ProviderId;
import dev.challenge.notifications.templates.TextTemplates;
import java.util.Map;

/** Render plain text first, then reuse normal notification validation and sending. */
public class TemplateDemo {
    public static void main(String[] args) {
        var variables = Map.of("name", "Ana", "order", "A-123");
        String body = TextTemplates.render("Hola {{name}}, recibimos tu pedido {{order}}.", variables);
        if (!body.equals("Hola Ana, recibimos tu pedido A-123.")) {
            throw new AssertionError("Template rendering failed");
        }
        var provider = new ProviderId("sendgrid");
        var client = Notifications.builder()
                .register(EmailNotification.class, provider, new SimulatedSendGridSender(
                        new SendGridConfig(provider, "FAKE_KEY", new EmailAddress("sender@example.com")),
                        SendGridSimulation.ACCEPTED))
                .defaultProvider(EmailNotification.class, provider).build();
        var receipt = client.send(new EmailNotification(new EmailAddress("demo@example.com"), "Pedido recibido", body));
        if (!receipt.simulated()) {
            throw new AssertionError("Demo must remain simulated");
        }
        System.out.println("Template rendered and Email accepted (simulated=true)");
    }
}
