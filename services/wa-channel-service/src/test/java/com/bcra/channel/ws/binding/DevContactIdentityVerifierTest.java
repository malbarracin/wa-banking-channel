package com.bcra.channel.ws.binding;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.bcra.channel.ws.binding.service.DevContactIdentityVerifier;
import com.bcra.channel.ws.client.UserLookupResult;
import com.bcra.channel.ws.client.UserLookupStatus;

class DevContactIdentityVerifierTest {

    private final DevContactIdentityVerifier verifier = new DevContactIdentityVerifier();

    @Test
    @DisplayName("Coincide con contactPhone")
    void matchesPhone() {
        UserLookupResult user = activeUser("+5491112345678", "ana@example.com");
        assertThat(verifier.matches(user, "+5491112345678")).isTrue();
    }

    @Test
    @DisplayName("Coincide con contactEmail sin importar mayúsculas")
    void matchesEmailIgnoreCase() {
        UserLookupResult user = activeUser("+5491112345678", "ana@example.com");
        assertThat(verifier.matches(user, "ANA@EXAMPLE.COM")).isTrue();
    }

    @Test
    @DisplayName("Dato incorrecto no coincide")
    void rejectsMismatch() {
        UserLookupResult user = activeUser("+5491112345678", "ana@example.com");
        assertThat(verifier.matches(user, "otro")).isFalse();
    }

    private static UserLookupResult activeUser(String phone, String email) {
        return new UserLookupResult(
                UserLookupStatus.FOUND_ACTIVE,
                UUID.randomUUID(),
                "USR-1",
                "Ana",
                email,
                phone);
    }
}
