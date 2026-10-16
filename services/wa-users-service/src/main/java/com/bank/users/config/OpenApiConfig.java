package com.bank.users.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;

/** Metadatos de la API de usuarios del hito H1. */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI usersOpenApi() {
        return new OpenAPI().info(new Info().title("Servicio de usuarios")
                .version("v1").description("Registro de usuarios bancarios del canal WhatsApp"));
    }
}
