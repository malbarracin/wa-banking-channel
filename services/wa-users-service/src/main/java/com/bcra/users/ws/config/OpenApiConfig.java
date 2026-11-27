package com.bcra.users.ws.config;

import java.util.LinkedHashMap;
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
                        .addExamples("ValidationError", example(ordered(
                                "code", "VALIDATION_ERROR",
                                "message", "Validation failed",
                                "details", List.of(ordered(
                                        "field", "contactEmail",
                                        "message", "must be a well-formed email address")),
                                "timestamp", ERROR_EXAMPLE_TIMESTAMP)))
                        .addExamples("BadRequest", example(ordered(
                                "code", "BAD_REQUEST",
                                "message", "Invalid request",
                                "details", List.of(),
                                "timestamp", ERROR_EXAMPLE_TIMESTAMP)))
                        .addExamples("NotFound", example(ordered(
                                "code", "NOT_FOUND",
                                "message", "No existe el usuario solicitado",
                                "details", List.of(),
                                "timestamp", ERROR_EXAMPLE_TIMESTAMP)))
                        .addExamples("Duplicate", example(ordered(
                                "code", "CONFLICT",
                                "message", "DUPLICADO",
                                "details", List.of(ordered(
                                        "field", "uniquenessValue",
                                        "message", "DUPLICADO")),
                                "timestamp", ERROR_EXAMPLE_TIMESTAMP)))
                        .addExamples("InvalidTransition", example(ordered(
                                "code", "CONFLICT",
                                "message", "Transición de estado no permitida",
                                "details", List.of(
                                        ordered("field", "previousStatus", "message", "INACTIVE"),
                                        ordered("field", "newStatus", "message", "ACTIVE"),
                                        ordered("field", "reason", "message", "TRANSICION_NO_PERMITIDA")),
                                "timestamp", ERROR_EXAMPLE_TIMESTAMP)))
                        .addExamples("InternalError", example(ordered(
                                "code", "INTERNAL_ERROR",
                                "message", "Internal error",
                                "details", List.of(),
                                "timestamp", ERROR_EXAMPLE_TIMESTAMP))));
    }

    private static Example example(Object value) {
        return new Example().value(value);
    }

    /** Mapa ordenado para que el OpenAPI exportado no reordene claves entre builds. */
    private static Map<String, Object> ordered(Object... keysAndValues) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < keysAndValues.length; i += 2) {
            map.put((String) keysAndValues[i], keysAndValues[i + 1]);
        }
        return map;
    }
}
