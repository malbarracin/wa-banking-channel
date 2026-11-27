package com.bcra.channel.ws.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class BankChannelPropertiesTest {

    @Autowired
    private BankChannelProperties properties;

    @Test
    @DisplayName("Enlaza todos los parámetros bank.channel.* desde la configuración de test")
    void bindsAllBankChannelProperties() {
        assertThat(properties.getVerification().getPolicy()).isEqualTo("document-code");
        assertThat(properties.getVerification().getMaxAttempts()).isEqualTo(3);
        assertThat(properties.getVerification().getWindowMinutes()).isEqualTo(60);
        assertThat(properties.getName()).isEqualTo("Banco Demo");
        assertThat(properties.getSupportPhone()).isEqualTo("0800-123-4567");
        assertThat(properties.getOnboardingUrl()).isEqualTo("https://banco.example/alta");
        assertThat(properties.getInternalRoles().getSupport()).isEqualTo("support");
        assertThat(properties.getInternalRoles().getBankPolicy()).isEqualTo("bank-policy");
        assertThat(properties.getUsersService().getBaseUrl()).isEqualTo("http://localhost:18081");
        assertThat(properties.getUsersService().getTimeoutMs()).isEqualTo(2500);
    }
}
