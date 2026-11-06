package dev.challenge.notifications.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class ContractsTest {
    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "UPPER", "a_b", "-first", "1first", "secret\n"})
    void rejectsInvalidProviderIds(String id) {
        assertThrows(NotificationConfigurationException.class, () -> new ProviderId(id));
    }

    @Test
    void providerIdLengthAndEquality() {
        assertEquals(new ProviderId("sendgrid"), new ProviderId("sendgrid"));
        assertDoesNotThrow(() -> new ProviderId("a".repeat(64)));
        assertThrows(NotificationConfigurationException.class, () -> new ProviderId("a".repeat(65)));
    }

    @Test
    void receiptsValidateTheirOwnInvariantsAndRedactRemoteIdentifiers() {
        var provider = new ProviderId("demo");
        assertThrows(IllegalArgumentException.class, () -> new SendReceipt(null, Optional.empty(), true));
        assertThrows(IllegalArgumentException.class, () -> new SendReceipt(provider, null, true));
        assertThrows(IllegalArgumentException.class, () -> new SendReceipt(provider, Optional.of(" "), true));
        var receipt = new SendReceipt(provider, Optional.of("PRIVATE_REMOTE_ID"), true);
        assertFalse(receipt.toString().contains("PRIVATE_REMOTE_ID"));
        assertEquals("PRIVATE_REMOTE_ID", receipt.providerMessageId().orElseThrow());
    }
}
