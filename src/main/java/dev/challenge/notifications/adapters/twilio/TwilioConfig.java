package dev.challenge.notifications.adapters.twilio;

import dev.challenge.notifications.channels.sms.PhoneNumber;
import dev.challenge.notifications.domain.NotificationConfigurationException;
import dev.challenge.notifications.domain.ProviderId;

/**
 * Caller-supplied Twilio settings; no account or credential is verified remotely.
 * All credential fields must be nonblank and contain no CR/LF.
 * @param providerId the non-secret routing ID used to register this sender
 * @param accountSid account identifier for the request path
 * @param apiKeySid Basic authentication username
 * @param secret Basic authentication secret; use fictional values in the simulator
 * @param from configured sender in syntactic E.164 format
 */
public record TwilioConfig(ProviderId providerId, String accountSid, String apiKeySid,
                           String secret, PhoneNumber from) {
    public TwilioConfig {
        if (providerId == null || from == null || !validCredential(accountSid)
                || !validCredential(apiKeySid) || !validCredential(secret)) {
            throw new NotificationConfigurationException("INVALID_TWILIO_CONFIG");
        }
    }

    private static boolean validCredential(String value) {
        return value != null && !value.isBlank() && !value.contains("\r") && !value.contains("\n");
    }

    @Override
    public String toString() {
        return "TwilioConfig[REDACTED]";
    }
}
