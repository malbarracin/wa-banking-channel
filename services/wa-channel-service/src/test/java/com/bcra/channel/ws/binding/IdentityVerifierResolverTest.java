package com.bcra.channel.ws.binding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.bcra.channel.ws.binding.service.DevContactIdentityVerifier;
import com.bcra.channel.ws.binding.service.IdentityVerifier;
import com.bcra.channel.ws.binding.service.IdentityVerifierResolver;
import com.bcra.channel.ws.client.UserLookupResult;
import com.bcra.channel.ws.client.UserLookupStatus;
import com.bcra.channel.ws.config.BankChannelProperties;

class IdentityVerifierResolverTest {

    @Test
    @DisplayName("Política conocida resuelve el verificador configurado")
    void knownPolicyUsesConfiguredVerifier() {
        BankChannelProperties properties = new BankChannelProperties();
        properties.getVerification().setPolicy(DevContactIdentityVerifier.POLICY);
        IdentityVerifierResolver resolver = new IdentityVerifierResolver(
                properties, List.of(new DevContactIdentityVerifier()));

        UserLookupResult user = new UserLookupResult(
                UserLookupStatus.FOUND_ACTIVE,
                UUID.randomUUID(),
                "USR-1",
                "Ana",
                "ana@example.com",
                "+5491112345678");

        assertThat(resolver.verify(user, "+5491112345678")).isTrue();
        assertThat(resolver.verify(user, "otro")).isFalse();
    }

    @Test
    @DisplayName("Política desconocida falla al construir el bean")
    void unknownPolicyFailsAtConstruction() {
        BankChannelProperties properties = new BankChannelProperties();
        properties.getVerification().setPolicy("politica-inexistente");
        List<IdentityVerifier> verifiers = List.of(new DevContactIdentityVerifier());

        assertThatThrownBy(() -> new IdentityVerifierResolver(properties, verifiers))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("politica-inexistente")
                .hasMessageContaining(DevContactIdentityVerifier.POLICY);
    }
}
