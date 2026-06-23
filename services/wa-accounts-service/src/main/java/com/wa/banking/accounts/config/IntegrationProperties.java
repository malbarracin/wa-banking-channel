package com.wa.banking.accounts.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Propiedades de integración con el servicio H2 (acceso y sesión).
 *
 * @author licius-it
 * @since 2026-06-23
 * @contact marceloalejandro.albarracin@gmail.com
 */
@Data
@ConfigurationProperties(prefix = "integration")
public class IntegrationProperties {

    private Session session = new Session();

    @Data
    public static class Session {

        private String baseUrl = "http://localhost:8082";

        private boolean stubEnabled = true;
    }
}
