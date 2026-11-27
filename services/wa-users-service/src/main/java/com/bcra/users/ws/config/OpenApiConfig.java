package com.bcra.users.ws.config;

import java.util.List;
import java.util.Map;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.examples.Example;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Metadatos OpenAPI del servicio y ejemplos reutilizables del contrato de error.
 */
@Configuration
public class OpenApiConfig {

    private static final String ERROR_EXAMPLE_TIMESTAMP = "2026-11-27T12:00:00Z";

    @Bean
    OpenAPI usersOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("wa-users-service")
                        .version("0.1.0-SNAPSHOT")
                        .description("Registro único de clientes del banco: alta, consulta, "
                                + "actualización de datos permitidos, cambio de estado operativo y auditoría."))
                .servers(List.of(new Server().url("http://localhost:8080").description("Local")))
                .components(new Components()
                        .addExamples("ValidationError", example(Map.of(
                                "code", "VALIDATION_ERROR",
                                "message", "Validation failed",
                                "details", List.of(Map.of(
                                        "field", "contactEmail",
                                        "message", "must be a well-formed email address")),
                                "timestamp", ERROR_EXAMPLE_TIMESTAMP)))
                        .addExamples("BadRequest", example(Map.of(
                                "code", "BAD_REQUEST",
                                "message", "Invalid request",
                                "details", List.of(),
                                "timestamp", ERROR_EXAMPLE_TIMESTAMP)))
                        .addExamples("NotFound", example(Map.of(
                                "code", "NOT_FOUND",
                                "message", "No existe el usuario solicitado",
                                "details", List.of(),
                                "timestamp", ERROR_EXAMPLE_TIMESTAMP)))
                        .addExamples("Duplicate", example(Map.of(
                                "code", "CONFLICT",
                                "message", "DUPLICADO",
                                "details", List.of(Map.of(
                                        "field", "uniquenessValue",
                                        "message", "DUPLICADO")),
                                "timestamp", ERROR_EXAMPLE_TIMESTAMP)))
                        .addExamples("InvalidTransition", example(Map.of(
                                "code", "CONFLICT",
                                "message", "Transición de estado no permitida",
                                "details", List.of(
                                        Map.of("field", "previousStatus", "message", "INACTIVE"),
                                        Map.of("field", "newStatus", "message", "ACTIVE"),
                                        Map.of("field", "reason", "message", "TRANSICION_NO_PERMITIDA")),
                                "timestamp", ERROR_EXAMPLE_TIMESTAMP)))
                        .addExamples("InternalError", example(Map.of(
                                "code", "INTERNAL_ERROR",
                                "message", "Internal error",
                                "details", List.of(),
                                "timestamp", ERROR_EXAMPLE_TIMESTAMP))));
    }

    private static Example example(Object value) {
        return new Example().value(value);
    }
}
