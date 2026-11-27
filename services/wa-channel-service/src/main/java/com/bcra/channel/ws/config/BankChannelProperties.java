package com.bcra.channel.ws.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.Setter;

/**
 * Parámetros configurables del canal WhatsApp bajo el prefijo {@code bank.channel}.
 * Los valores por defecto son solo de desarrollo; el banco define los de cada entorno.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "bank.channel")
public class BankChannelProperties {

    private Verification verification = new Verification();
    private String name = "Banco";
    private String supportPhone = "0800-XXX-XXXX";
    private String onboardingUrl = "https://example.com/alta";
    private InternalRoles internalRoles = new InternalRoles();
    private UsersService usersService = new UsersService();

    /**
     * Política y límites de verificación de identidad.
     */
    @Getter
    @Setter
    public static class Verification {

        private String policy = "document-code";
        private int maxAttempts = 3;
        private int windowMinutes = 60;
    }

    /**
     * Valores esperados del header {@code X-Internal-Role}.
     */
    @Getter
    @Setter
    public static class InternalRoles {

        private String support = "support";
        private String bankPolicy = "bank-policy";
    }

    /**
     * Cliente HTTP hacia el servicio de usuarios (H1).
     */
    @Getter
    @Setter
    public static class UsersService {

        private String baseUrl = "http://localhost:8081";
        private int timeoutMs = 3000;
    }
}
