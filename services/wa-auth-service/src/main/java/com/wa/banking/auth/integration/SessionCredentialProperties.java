package com.wa.banking.auth.integration;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Propiedades de configuración de credenciales de sesión (TTL y pepper).
 *
 * @author licius-it
 * @since 2026-06-23
 * @contact marceloalejandro.albarracin@gmail.com
 */
@Data
@ConfigurationProperties(prefix = "session.credential")
public class SessionCredentialProperties {

    private int ttlHours = 24;

    private String pepper = "change-me-in-production";
}
