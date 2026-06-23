package com.wa.banking.channel.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Propiedades de integración con servicios upstream H1 (usuarios) y H2 (sesión).
 *
 * @author licius-it
 * @since 2026-06-23
 * @contact marceloalejandro.albarracin@gmail.com
 */
@Data
@ConfigurationProperties(prefix = "integration")
public class IntegrationProperties {

    private Users users = new Users();

    private Session session = new Session();

    @Data
    public static class Users {

        private String baseUrl = "http://localhost:8080";
    }

    @Data
    public static class Session {

        private String baseUrl = "http://localhost:8082";

        private boolean stubEnabled = true;
    }
}
