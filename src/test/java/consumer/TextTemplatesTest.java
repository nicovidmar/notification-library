package consumer;

import dev.challenge.notifications.channels.email.EmailAddress;
import dev.challenge.notifications.channels.email.EmailNotification;
import dev.challenge.notifications.channels.sms.PhoneNumber;
import dev.challenge.notifications.channels.sms.SmsNotification;
import dev.challenge.notifications.channels.push.DeviceToken;
import dev.challenge.notifications.channels.push.PushNotification;
import dev.challenge.notifications.domain.NotificationValidationException;
import dev.challenge.notifications.templates.TextTemplates;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.HashMap;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class TextTemplatesTest {
    @Test
    void rendersNamedRepeatedAndAdjacentVariables() {
        var variables = Map.of("nombre", "Ana", "pedido", "A-123");
        assertEquals("Hola Ana, pedido A-123. Ana",
                TextTemplates.render("Hola {{nombre}}, pedido {{pedido}}. {{nombre}}", variables));
        assertEquals("AnaA-123", TextTemplates.render("{{nombre}}{{pedido}}", variables));
    }

    @Test
    void leavesPlainTextAndEmptyTemplatesUnchanged() {
        assertEquals("", TextTemplates.render("", Map.of()));
        assertEquals("Hola {nombre} — café", TextTemplates.render("Hola {nombre} — café", Map.of()));
    }

    @Test
    void insertsValuesLiterallyWithoutRecursiveRendering() {
        String value = "$1\\folder\n{{other}}}}";
        assertEquals(value, TextTemplates.render("{{value}}", Map.of("value", value, "other", "changed")));
    }

    @Test
    void allowsEmptyValuesAndIgnoresUnusedVariablesWithoutMutatingTheMap() {
        var variables = new HashMap<String, String>();
        variables.put("name", "");
        variables.put("unused", null);
        assertEquals("Hi ", TextTemplates.render("Hi {{name}}", variables));
        assertEquals(2, variables.size());
        assertTrue(variables.containsKey("unused"));
        variables.put("name", " ");
        assertEquals(" ", TextTemplates.render("{{name}}", variables));
    }

    @ParameterizedTest
    @ValueSource(strings = {"{{", "}}", "a}}b", "{{name", "{{}}", "{{ name}}", "{{name }}",
            "{{1name}}", "{{a-b}}", "{{a.b}}", "{{á}}", "{{{name}}", "{{a{{b}}", "{{a}}}}"})
    void rejectsMalformedMarkers(String template) {
        var error = assertThrows(NotificationValidationException.class,
                () -> TextTemplates.render(template, Map.of("a", "ok", "name", "ok")));
        assertEquals("template", error.field());
        assertEquals("INVALID_PLACEHOLDER", error.code());
    }

    @Test
    void supportsTheDeclaredVariableNamesAndCaseSensitivity() {
        assertEquals("ABC", TextTemplates.render("{{a}}{{nombre_2}}{{Name}}",
                Map.of("a", "A", "nombre_2", "B", "Name", "C")));
        assertEquals("MISSING_VARIABLE", assertThrows(NotificationValidationException.class,
                () -> TextTemplates.render("{{Name}}", Map.of("name", "other"))).code());
    }

    @Test
    void distinguishesMissingAndNullReferencedValues() {
        assertEquals("MISSING_VARIABLE", assertThrows(NotificationValidationException.class,
                () -> TextTemplates.render("{{name}}", Map.of())).code());
        var variables = new HashMap<String, String>();
        variables.put("name", null);
        assertEquals("NULL_VARIABLE", assertThrows(NotificationValidationException.class,
                () -> TextTemplates.render("{{name}}", variables)).code());
    }

    @Test
    void rejectsNullInputsWithSafeContext() {
        assertEquals("template", assertThrows(NotificationValidationException.class,
                () -> TextTemplates.render(null, Map.of())).field());
        assertEquals("variables", assertThrows(NotificationValidationException.class,
                () -> TextTemplates.render("text", null)).field());
        var failure = assertThrows(NotificationValidationException.class,
                () -> TextTemplates.render("PRIVATE {{missing}}", Map.of("secret", "PRIVATE_SECRET")));
        assertFalse(failure.toString().contains("PRIVATE"));
        assertNull(failure.getCause());
    }

    @Test
    void renderedTextUsesExistingChannelValidation() {
        String body = TextTemplates.render("Hola {{name}}", Map.of("name", "Ana"));
        assertEquals(body, new EmailNotification(new EmailAddress("a@example.com"), "Hola", body).body());
        assertEquals(body, new SmsNotification(new PhoneNumber("+123456789"), body).body());
        assertEquals(body, new PushNotification(new DeviceToken("opaque"), "Hola", body).body());
        String subject = TextTemplates.render("{{subject}}", Map.of("subject", "line1\nline2"));
        assertThrows(NotificationValidationException.class,
                () -> new EmailNotification(new EmailAddress("a@example.com"), subject, body));
        String empty = TextTemplates.render("{{body}}", Map.of("body", ""));
        assertThrows(NotificationValidationException.class,
                () -> new SmsNotification(new PhoneNumber("+123456789"), empty));
    }
}
