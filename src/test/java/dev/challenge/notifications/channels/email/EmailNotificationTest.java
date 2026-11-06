package dev.challenge.notifications.channels.email;

import dev.challenge.notifications.domain.NotificationValidationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class EmailNotificationTest {
    @ParameterizedTest
    @ValueSource(strings = {"demo+tag@example.com", "A.b_1%tag@sub.example.com", "a@x.co"})
    void acceptsSupportedAddressesWithoutNormalization(String value) {
        assertEquals(value, new EmailAddress(value).value());
        assertEquals(new EmailAddress(value), new EmailAddress(value));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "no-at", "a@@example.com", "@example.com",
            ".a@example.com", "a.@example.com", "a..b@example.com", "a@localhost",
            "a@-example.com", "a@example-.com", "a@example..com", "a@example.com.",
            "a b@example.com", "a@example.com\n", "á@example.com", "\"a\"@example.com"})
    void rejectsUnsupportedAddressesWithSafeContext(String value) {
        var failure = assertThrows(NotificationValidationException.class, () -> new EmailAddress(value));
        assertEquals("to", failure.field());
        assertEquals("INVALID_EMAIL", failure.code());
        assertNull(failure.getCause());
    }

    @Test
    void enforcesAddressLengthBoundaries() {
        assertDoesNotThrow(() -> new EmailAddress("a".repeat(64) + "@example.com"));
        assertThrows(NotificationValidationException.class,
                () -> new EmailAddress("a".repeat(65) + "@example.com"));
        assertDoesNotThrow(() -> new EmailAddress("a@" + "b".repeat(63) + ".com"));
        assertThrows(NotificationValidationException.class,
                () -> new EmailAddress("a@" + "b".repeat(64) + ".com"));
        String domain = "b".repeat(63) + "." + "c".repeat(63) + "." + "d".repeat(61);
        assertEquals(254, new EmailAddress("a".repeat(64) + "@" + domain).value().length());
        assertThrows(NotificationValidationException.class,
                () -> new EmailAddress("a".repeat(64) + "@" + domain + "d"));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t", "\n", "subject\r\ninjected", "subject\n"})
    void rejectsInvalidSubjects(String subject) {
        var failure = assertThrows(NotificationValidationException.class,
                () -> new EmailNotification(new EmailAddress("demo@example.com"), subject, "body"));
        assertEquals("subject", failure.field());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t", "\n"})
    void rejectsBlankBody(String body) {
        assertThrows(NotificationValidationException.class,
                () -> new EmailNotification(new EmailAddress("demo@example.com"), "subject", body));
    }

    @Test
    void rejectsNullRecipientAndPreservesMultilineBody() {
        assertThrows(NotificationValidationException.class, () -> new EmailNotification(null, "Hi", "Body"));
        var message = new EmailNotification(new EmailAddress("demo@example.com"), " Hi ", " Line 1\nLine 2 ");
        assertEquals(" Hi ", message.subject());
        assertEquals(" Line 1\nLine 2 ", message.body());
    }

    @Test
    void diagnosticStringsDoNotRevealRecipientOrContent() {
        var message = new EmailNotification(new EmailAddress("private@example.com"), "PRIVATE_SUBJECT", "PRIVATE_BODY");
        assertFalse(message.toString().contains("PRIVATE"));
        assertFalse(message.toString().contains("private@example.com"));
        assertFalse(message.to().toString().contains("private@example.com"));
    }
}
