package com.bcra.ws.users.config;

import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.examples.Example;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.servers.Server;

/**
 * Configuración OpenAPI / Swagger UI (grupo API v1) con ejemplos de error reutilizables.
 */
@Configuration
public class OpenApiConfig {

    /**
     * Metadatos generales, servidor local y ejemplos del contrato de error.
     *
     * @return documento OpenAPI
     */
    @Bean
    public OpenAPI waUsersOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("wa-users-service API")
                        .description("Servicio de Usuarios (H1): alta, consulta, actualización y estado "
                                + "operativo de clientes bancarios. No gestiona WhatsApp ni credenciales de canal.")
                        .version("0.0.1-SNAPSHOT")
                        .contact(new Contact()
                                .name("Equipo canal bancario")))
                .addServersItem(new Server()
                        .url("http://localhost:8080")
                        .description("Local"))
                .components(new Components()
                        .addSchemas("ErrorResponse", new Schema<>()
                                .type("object")
                                .description("Contrato único de error HTTP")
                                .addProperty("code", new Schema<>().type("string")
                                        .description("VALIDATION_ERROR | BAD_REQUEST | NOT_FOUND | CONFLICT | INTERNAL_ERROR"))
                                .addProperty("message", new Schema<>().type("string"))
                                .addProperty("details", new Schema<>().type("array")
                                        .items(new Schema<>().type("object")
                                                .addProperty("field", new Schema<>().type("string"))
                                                .addProperty("message", new Schema<>().type("string"))))
                                .addProperty("timestamp", new Schema<>().type("string").format("date-time")))
                        .addExamples("ValidationError", example("""
                                {
                                  "code": "VALIDATION_ERROR",
                                  "message": "Request validation failed",
                                  "details": [
                                    { "field": "email", "message": "email must be valid" }
                                  ],
                                  "timestamp": "2026-10-19T12:00:00Z"
                                }
                                """))
                        .addExamples("BadRequest", example("""
                                {
                                  "code": "BAD_REQUEST",
                                  "message": "Invalid request parameters",
                                  "details": [],
                                  "timestamp": "2026-10-19T12:00:00Z"
                                }
                                """))
                        .addExamples("NonUpdatableField", example("""
                                {
                                  "code": "BAD_REQUEST",
                                  "message": "Field is not updatable in this service",
                                  "details": [
                                    { "field": "documentNumber", "message": "Field is not updatable in this service" }
                                  ],
                                  "timestamp": "2026-10-19T12:00:00Z"
                                }
                                """))
                        .addExamples("NotFound", example("""
                                {
                                  "code": "NOT_FOUND",
                                  "message": "User not found",
                                  "details": [],
                                  "timestamp": "2026-10-19T12:00:00Z"
                                }
                                """))
                        .addExamples("DuplicateDocument", example("""
                                {
                                  "code": "CONFLICT",
                                  "message": "A user with the same document already exists",
                                  "details": [
                                    { "field": "documentNumber", "message": "Document already registered" }
                                  ],
                                  "timestamp": "2026-10-19T12:00:00Z"
                                }
                                """))
                        .addExamples("DuplicateEmail", example("""
                                {
                                  "code": "CONFLICT",
                                  "message": "A user with the same email already exists",
                                  "details": [
                                    { "field": "email", "message": "Email already registered" }
                                  ],
                                  "timestamp": "2026-10-19T12:00:00Z"
                                }
                                """))
                        .addExamples("InvalidTransition", example("""
                                {
                                  "code": "CONFLICT",
                                  "message": "Invalid status transition from ACTIVE to ACTIVE",
                                  "details": [
                                    { "field": "status", "message": "Status transition is not allowed" }
                                  ],
                                  "timestamp": "2026-10-19T12:00:00Z"
                                }
                                """))
                        .addExamples("InternalError", example("""
                                {
                                  "code": "INTERNAL_ERROR",
                                  "message": "An unexpected error occurred",
                                  "details": [],
                                  "timestamp": "2026-10-19T12:00:00Z"
                                }
                                """)));
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

    private static Example example(String value) {
        return new Example().value(value);
    }
}
