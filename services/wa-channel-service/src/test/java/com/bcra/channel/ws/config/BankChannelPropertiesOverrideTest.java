package com.bcra.channel.ws.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class BankChannelPropertiesOverrideTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(PropertiesConfiguration.class);

    @Test
    @DisplayName("Enlaza cada parámetro bank.channel.* cuando el entorno define valores propios")
    void bindsOverriddenValues() {
        contextRunner
                .withPropertyValues(
                        "bank.channel.verification.policy=otp-sms",
                        "bank.channel.verification.max-attempts=5",
                        "bank.channel.verification.window-minutes=15",
                        "bank.channel.name=Banco Prueba",
                        "bank.channel.support-phone=0800-999-0000",
                        "bank.channel.onboarding-url=https://alta.prueba/inicio",
                        "bank.channel.internal-roles.support=soporte-n1",
                        "bank.channel.internal-roles.bank-policy=riesgo",
                        "bank.channel.users-service.base-url=http://users:9000",
                        "bank.channel.users-service.timeout-ms=1200")
                .run(context -> {
                    BankChannelProperties properties = context.getBean(BankChannelProperties.class);
                    assertThat(properties.getVerification().getPolicy()).isEqualTo("otp-sms");
                    assertThat(properties.getVerification().getMaxAttempts()).isEqualTo(5);
                    assertThat(properties.getVerification().getWindowMinutes()).isEqualTo(15);
                    assertThat(properties.getName()).isEqualTo("Banco Prueba");
                    assertThat(properties.getSupportPhone()).isEqualTo("0800-999-0000");
                    assertThat(properties.getOnboardingUrl()).isEqualTo("https://alta.prueba/inicio");
                    assertThat(properties.getInternalRoles().getSupport()).isEqualTo("soporte-n1");
                    assertThat(properties.getInternalRoles().getBankPolicy()).isEqualTo("riesgo");
                    assertThat(properties.getUsersService().getBaseUrl()).isEqualTo("http://users:9000");
                    assertThat(properties.getUsersService().getTimeoutMs()).isEqualTo(1200);
                });
    }

    @Test
    @DisplayName("Sin configuración, arranca con valores de desarrollo utilizables")
    void usesDevelopmentDefaultsWhenNothingIsConfigured() {
        contextRunner.run(context -> {
            BankChannelProperties properties = context.getBean(BankChannelProperties.class);
            assertThat(properties.getVerification().getMaxAttempts()).isPositive();
            assertThat(properties.getVerification().getWindowMinutes()).isPositive();
            assertThat(properties.getUsersService().getBaseUrl()).isNotBlank();
            assertThat(properties.getUsersService().getTimeoutMs()).isPositive();
        });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(BankChannelProperties.class)
    static class PropertiesConfiguration {
    }
}
