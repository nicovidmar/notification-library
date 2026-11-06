import dev.challenge.notifications.adapters.slack.SimulatedSlackSender;
import dev.challenge.notifications.adapters.slack.SlackConfig;
import dev.challenge.notifications.adapters.slack.SlackSimulation;
import dev.challenge.notifications.channels.slack.SlackNotification;
import dev.challenge.notifications.configuration.Notifications;
import dev.challenge.notifications.domain.ProviderId;

/** Local simulation; no real webhook or Slack account required. */
public class SlackDemo {
    public static void main(String[] args) {
        var id = new ProviderId("slack-alertas");
        var config = new SlackConfig(id,
                "https://hooks.slack.com/services/FAKE/DEMO/not-a-secret");
        var client = Notifications.builder()
                .register(SlackNotification.class, id,
                        new SimulatedSlackSender(config, SlackSimulation.ACCEPTED))
                .defaultProvider(SlackNotification.class, id)
                .build();
        var receipt = client.send(new SlackNotification("Proceso terminado: 3 < 5 & todo listo."));
        if (!receipt.simulated() || !receipt.providerId().equals(id) || receipt.providerMessageId().isPresent()) {
            throw new AssertionError("Unexpected Slack receipt");
        }
        System.out.println("SlackDemo: simulated acceptance, no network or remote message ID.");
    }
}
