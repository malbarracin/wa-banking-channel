package com.bcra.channel.ws.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class ChannelMessagesTest {

    private static final List<String> FORBIDDEN_FRAGMENTS = List.of(
            "bindingId",
            "clientId",
            "token",
            "verificationCode",
            "codigo-verificacion");

    @Autowired
    private ChannelMessages channelMessages;

    @Autowired
    private BankChannelProperties properties;

    @Test
    @DisplayName("Resuelve 8.2 y 8.4–8.10 con banco, soporte y URL de alta, sin secretos")
    void resolvesCatalogWithoutSecrets() {
        String termsRejected = channelMessages.termsRejected();
        String clientNotFound = channelMessages.clientNotFound();
        String verificationFailed = channelMessages.verificationFailed(2);
        String verificationLocked = channelMessages.verificationLocked();
        String numberAlreadyLinked = channelMessages.numberAlreadyLinked();
        String welcome = channelMessages.welcome("Ana");
        String clientSuspended = channelMessages.clientSuspended();
        String credentialReverify = channelMessages.credentialReverify();
        String blocked = channelMessages.blocked();
        String unbound = channelMessages.unbound();

        assertThat(termsRejected).contains("condiciones");
        assertThat(clientNotFound)
                .contains(properties.getName())
                .contains(properties.getOnboardingUrl());
        assertThat(verificationFailed)
                .contains("2")
                .contains(properties.getSupportPhone());
        assertThat(verificationLocked).contains(properties.getSupportPhone());
        assertThat(numberAlreadyLinked).contains(properties.getSupportPhone());
        assertThat(welcome).contains("Ana");
        assertThat(clientSuspended).contains(properties.getSupportPhone());
        assertThat(credentialReverify).contains("verificar");
        assertThat(blocked).contains("bloqueado");
        assertThat(unbound).contains("desvinculado");

        List<String> rendered = List.of(
                termsRejected,
                clientNotFound,
                verificationFailed,
                verificationLocked,
                numberAlreadyLinked,
                welcome,
                clientSuspended,
                credentialReverify,
                blocked,
                unbound);

        for (String message : rendered) {
            for (String fragment : FORBIDDEN_FRAGMENTS) {
                assertThat(message).doesNotContain(fragment);
            }
        }
    }
}
