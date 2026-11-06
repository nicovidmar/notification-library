package consumer;

import dev.challenge.notifications.channels.push.DeviceToken;
import dev.challenge.notifications.channels.sms.PhoneNumber;
import dev.challenge.notifications.domain.NotificationValidationException;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RecipientFieldTest {
    @Test
    void invalidRecipientsUseTheSamePublicField() {
        assertEquals("to", assertThrows(NotificationValidationException.class, () -> new PhoneNumber("invalid")).field());
        assertEquals("to", assertThrows(NotificationValidationException.class, () -> new DeviceToken(" ")).field());
    }
}
