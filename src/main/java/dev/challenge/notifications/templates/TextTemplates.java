package dev.challenge.notifications.templates;

import dev.challenge.notifications.domain.NotificationValidationException;

import java.util.Map;
import java.util.regex.Pattern;

/**
 * Stateless plain-text substitution before constructing a notification.
 * Markers are case-sensitive {{name}}, with ASCII names [A-Za-z][A-Za-z0-9_]*.
 * Values are inserted literally in one pass; no expressions, recursive expansion or escaping.
 * Delimiters {{ and }} are reserved and malformed markers cause validation errors.
 */
public final class TextTemplates {
    private static final Pattern NAME = Pattern.compile("[A-Za-z][A-Za-z0-9_]*");

    private TextTemplates() { }

    /**
     * Renders a template without retaining or modifying inputs. The caller must not modify
     * the map during this call. Empty values are allowed; unused variables are ignored.
     * Validation errors expose fixed field/code identifiers, never template text or values.
     *
     * @param template plain text, possibly empty, but never null
     * @param variables literal string values keyed by variable name
     * @return rendered text, to be validated by the target notification type
     * @throws NotificationValidationException for null inputs, malformed markers or missing/null referenced values
     */
    public static String render(String template, Map<String, String> variables) {
        if (template == null) {
            throw new NotificationValidationException("template", "REQUIRED");
        }
        if (variables == null) {
            throw new NotificationValidationException("variables", "REQUIRED");
        }
        var result = new StringBuilder(template.length());
        int cursor = 0;
        while (cursor < template.length()) {
            int open = template.indexOf("{{", cursor);
            int close = template.indexOf("}}", cursor);
            if (close >= 0 && (open < 0 || close < open)) {
                throw invalidMarker();
            }
            if (open < 0) {
                result.append(template, cursor, template.length());
                break;
            }
            if (close < 0) {
                throw invalidMarker();
            }
            String name = template.substring(open + 2, close);
            if (!NAME.matcher(name).matches()) {
                throw invalidMarker();
            }
            if (!variables.containsKey(name)) {
                throw new NotificationValidationException("variables", "MISSING_VARIABLE");
            }
            String value = variables.get(name);
            if (value == null) {
                throw new NotificationValidationException("variables", "NULL_VARIABLE");
            }
            result.append(template, cursor, open).append(value);
            cursor = close + 2;
        }
        return result.toString();
    }

    private static NotificationValidationException invalidMarker() {
        return new NotificationValidationException("template", "INVALID_PLACEHOLDER");
    }
}
