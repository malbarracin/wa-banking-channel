package com.wa.banking.auth.integration;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Propiedades de integración con servicios upstream (H1 usuarios).
 *
 * @author licius-it
 * @since 2026-06-23
 * @contact marceloalejandro.albarracin@gmail.com
 */
@Data
@ConfigurationProperties(prefix = "integration")
public class IntegrationProperties {

    private Users users = new Users();

    @Data
    public static class Users {

        private boolean enabled = false;

        private String baseUrl = "http://localhost:8080";
    }
}
