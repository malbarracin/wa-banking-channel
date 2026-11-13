package com.bcra.ws.users.config;

import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;

/**
 * Configuración OpenAPI / Swagger UI (grupo API v1).
 *
 * @since 2026-10-19
 */
@Configuration
public class OpenApiConfig {

    /**
     * Metadatos generales de la API.
     *
     * @return documento OpenAPI
     */
    @Bean
    public OpenAPI waUsersOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("wa-users-service API")
                        .description("Servicio de Usuarios (H1) — canal bancario WhatsApp")
                        .version("v1")
                        .contact(new Contact()
                                .name("Ingenia")
                                .email("marcelo.albarracin@ingenia.la")));
    }

    /**
     * Grupo documentado para paths {@code /api/v1/**}.
     *
     * @return grupo OpenAPI v1
     */
    @Bean
    public GroupedOpenApi apiV1() {
        return GroupedOpenApi.builder()
                .group("v1")
                .pathsToMatch("/api/v1/**")
                .build();
    }
}
