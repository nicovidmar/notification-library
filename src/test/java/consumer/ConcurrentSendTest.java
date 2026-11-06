package consumer;

import dev.challenge.notifications.adapters.fcm.FcmConfig;
import dev.challenge.notifications.adapters.fcm.FcmSimulation;
import dev.challenge.notifications.adapters.fcm.SimulatedFcmSender;
import dev.challenge.notifications.adapters.mailgun.MailgunConfig;
import dev.challenge.notifications.adapters.mailgun.MailgunSimulation;
import dev.challenge.notifications.adapters.mailgun.SimulatedMailgunSender;
import dev.challenge.notifications.adapters.sendgrid.SendGridConfig;
import dev.challenge.notifications.adapters.sendgrid.SendGridSimulation;
import dev.challenge.notifications.adapters.sendgrid.SimulatedSendGridSender;
import dev.challenge.notifications.adapters.twilio.TwilioConfig;
import dev.challenge.notifications.adapters.twilio.TwilioSimulation;
import dev.challenge.notifications.adapters.twilio.SimulatedTwilioSender;
import dev.challenge.notifications.channels.email.EmailAddress;
import dev.challenge.notifications.channels.email.EmailNotification;
import dev.challenge.notifications.channels.push.DeviceToken;
import dev.challenge.notifications.channels.push.PushNotification;
import dev.challenge.notifications.channels.sms.PhoneNumber;
import dev.challenge.notifications.channels.sms.SmsNotification;
import dev.challenge.notifications.configuration.Notifications;
import dev.challenge.notifications.domain.ProviderId;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class ConcurrentSendTest {
    @Test
    void sharedClientRoutesConcurrentCallsWithThreadSafeSimulators() throws Exception {
        var sg = new ProviderId("sendgrid");
        var mg = new ProviderId("mailgun");
        var twilio = new ProviderId("twilio");
        var fcm = new ProviderId("fcm");
        var tokenCalls = new AtomicInteger();
        var from = new EmailAddress("sender@example.com");
        var client = Notifications.builder()
                .register(EmailNotification.class, sg, new SimulatedSendGridSender(
                        new SendGridConfig(sg, "FAKE_KEY", from), SendGridSimulation.ACCEPTED))
                .register(EmailNotification.class, mg, new SimulatedMailgunSender(
                        new MailgunConfig(mg, "mg.example.com", "FAKE_KEY", from), MailgunSimulation.ACCEPTED))
                .defaultProvider(EmailNotification.class, sg)
                .register(SmsNotification.class, twilio, new SimulatedTwilioSender(
                        new TwilioConfig(twilio, "AC_FAKE", "SK_FAKE", "FAKE_SECRET",
                                new PhoneNumber("+123456789")), TwilioSimulation.ACCEPTED))
                .defaultProvider(SmsNotification.class, twilio)
                .register(PushNotification.class, fcm, new SimulatedFcmSender(
                        new FcmConfig(fcm, "demo-project", () -> {
                            tokenCalls.incrementAndGet();
                            return "FAKE_TOKEN";
                        }), FcmSimulation.ACCEPTED))
                .defaultProvider(PushNotification.class, fcm).build();
        var email = new EmailNotification(new EmailAddress("recipient@example.com"), "Subject", "body");
        var sms = new SmsNotification(new PhoneNumber("+123456789"), "body");
        var push = new PushNotification(new DeviceToken("device"), "title", "body");
        int workers = 8;
        int rounds = 25;
        var ready = new CountDownLatch(workers);
        var start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(workers);
        var futures = new ArrayList<Future<?>>();
        try {
            for (int i = 0; i < workers; i++) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    if (!start.await(10, TimeUnit.SECONDS)) throw new AssertionError("start timed out");
                    for (int j = 0; j < rounds; j++) {
                        assertEquals(sg, client.send(email).providerId());
                        assertEquals(mg, client.send(email, mg).providerId());
                        assertEquals(twilio, client.send(sms).providerId());
                        assertEquals(fcm, client.send(push).providerId());
                    }
                    return null;
                }));
            }
            assertTrue(ready.await(10, TimeUnit.SECONDS), "workers failed to start");
            start.countDown();
            for (var future : futures) future.get(30, TimeUnit.SECONDS);
            assertEquals(workers * rounds, tokenCalls.get());
        } finally {
            start.countDown();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
        }
    }
}
