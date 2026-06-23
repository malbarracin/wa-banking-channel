package com.wa.banking.accounts.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * Configuración de beans de integración upstream (H2 sesión).
 *
 * @author licius-it
 * @since 2026-06-23
 * @contact marceloalejandro.albarracin@gmail.com
 */
@Configuration
@EnableConfigurationProperties(IntegrationProperties.class)
public class IntegrationConfig {

    @Bean
    public RestClient sessionRestClient(IntegrationProperties integrationProperties) {
        return RestClient.builder()
                .baseUrl(integrationProperties.getSession().getBaseUrl())
                .build();
    }
}
