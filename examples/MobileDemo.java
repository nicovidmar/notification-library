import dev.challenge.notifications.adapters.twilio.*;
import dev.challenge.notifications.adapters.fcm.*;
import dev.challenge.notifications.channels.sms.*;
import dev.challenge.notifications.channels.push.*;
import dev.challenge.notifications.configuration.Notifications;
import dev.challenge.notifications.domain.ProviderId;

/** Mobile examples run without a framework, files or real credentials. */
public class MobileDemo {
    public static void main(String[] args) {
        var twilio = new ProviderId("twilio");
        var fcm = new ProviderId("fcm");
        var client = Notifications.builder()
                .register(SmsNotification.class, twilio, new SimulatedTwilioSender(
                        new TwilioConfig(twilio, "AC_FAKE", "SK_FAKE", "FAKE_SECRET",
                                new PhoneNumber("+5491112345678")), TwilioSimulation.ACCEPTED))
                .defaultProvider(SmsNotification.class, twilio)
                .register(PushNotification.class, fcm, new SimulatedFcmSender(
                        new FcmConfig(fcm, "demo-project", () -> "FAKE_ACCESS_TOKEN"), FcmSimulation.ACCEPTED))
                .defaultProvider(PushNotification.class, fcm).build();
        var sms = client.send(new SmsNotification(new PhoneNumber("+5491187654321"), "Hola"));
        var push = client.send(new PushNotification(new DeviceToken("opaque-device-token"), "Título", "Hola"));
        if (!sms.simulated() || !push.simulated() || !sms.providerId().equals(twilio)
                || !push.providerId().equals(fcm) || sms.providerMessageId().isEmpty()
                || push.providerMessageId().isEmpty()) {
            throw new AssertionError("Mobile acceptance contract failed");
        }
        System.out.println("SMS: " + sms.providerId() + ", simulated=" + sms.simulated());
        System.out.println("Push: " + push.providerId() + ", simulated=" + push.simulated());
    }
}
