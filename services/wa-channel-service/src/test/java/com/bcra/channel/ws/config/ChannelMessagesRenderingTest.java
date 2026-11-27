package com.bcra.channel.ws.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.ResourceBundleMessageSource;

class ChannelMessagesRenderingTest {

    private ChannelMessages channelMessages;

    @BeforeEach
    void setUp() {
        ResourceBundleMessageSource messageSource = new ResourceBundleMessageSource();
        messageSource.setBasename("messages");
        messageSource.setDefaultEncoding(StandardCharsets.UTF_8.name());
        messageSource.setFallbackToSystemLocale(false);

        BankChannelProperties properties = new BankChannelProperties();
        properties.setName("Banco Norte");
        properties.setSupportPhone("0810-555-1234");
        properties.setOnboardingUrl("https://norte.example/hacete-cliente");
        channelMessages = new ChannelMessages(messageSource, properties);
    }

    @Test
    @DisplayName("Los mensajes toman banco, soporte y URL de alta de la configuración vigente")
    void usesConfiguredBankData() {
        assertThat(channelMessages.clientNotFound())
                .contains("Banco Norte")
                .contains("https://norte.example/hacete-cliente");
        assertThat(channelMessages.verificationFailed(1))
                .contains("Te quedan 1 intentos")
                .contains("0810-555-1234");
        assertThat(channelMessages.verificationLocked()).contains("0810-555-1234");
        assertThat(channelMessages.numberAlreadyLinked()).contains("0810-555-1234");
        assertThat(channelMessages.clientSuspended()).contains("0810-555-1234");
    }

    @Test
    @DisplayName("Ningún mensaje queda con placeholders sin resolver ni caracteres mal codificados")
    void rendersWithoutUnresolvedPlaceholders() {
        for (String message : allMessages()) {
            assertThat(message)
                    .isNotBlank()
                    .doesNotContain("{")
                    .doesNotContain("}")
                    .doesNotContain("�")
                    .doesNotContain("Ã");
        }
        assertThat(channelMessages.welcome("Ana")).startsWith("¡Listo, Ana!");
    }

    @Test
    @DisplayName("La bienvenida muestra solo el nombre, sin identificadores ni datos de verificación")
    void welcomeShowsOnlyTheName() {
        assertThat(channelMessages.welcome("Ana"))
                .contains("Ana")
                .doesNotContainIgnoringCase("bindingId")
                .doesNotContainIgnoringCase("clientId")
                .doesNotContainIgnoringCase("token")
                .doesNotContainIgnoringCase("código")
                .doesNotContainIgnoringCase("documento")
                .doesNotContainIgnoringCase("contraseña")
                .doesNotContainIgnoringCase("clave");
    }

    @Test
    @DisplayName("Ningún mensaje del catálogo usa vocabulario de secretos")
    void catalogHasNoSecretVocabulary() {
        for (String message : allMessages()) {
            assertThat(message)
                    .doesNotContainIgnoringCase("bindingId")
                    .doesNotContainIgnoringCase("clientId")
                    .doesNotContainIgnoringCase("token")
                    .doesNotContainIgnoringCase("contraseña")
                    .doesNotContainIgnoringCase("password");
        }
    }

    private List<String> allMessages() {
        return List.of(
                channelMessages.termsRejected(),
                channelMessages.clientNotFound(),
                channelMessages.verificationFailed(2),
                channelMessages.verificationLocked(),
                channelMessages.numberAlreadyLinked(),
                channelMessages.welcome("Ana"),
                channelMessages.clientSuspended(),
                channelMessages.credentialReverify(),
                channelMessages.blocked(),
                channelMessages.unbound());
    }
}
