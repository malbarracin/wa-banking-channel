package com.wa.banking.auth.integration;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Habilita propiedades de integración upstream para el módulo de sesión.
 *
 * @author licius-it
 * @since 2026-06-23
 * @contact marceloalejandro.albarracin@gmail.com
 */
@Configuration
@EnableConfigurationProperties({IntegrationProperties.class, SessionCredentialProperties.class})
public class IntegrationConfig {
}
