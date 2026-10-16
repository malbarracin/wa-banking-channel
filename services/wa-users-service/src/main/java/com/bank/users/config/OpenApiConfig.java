package com.bank.users.config;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.examples.Example;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;

/**
 * Metadatos OpenAPI del hito H1: contrato HTTP, ejemplos de error reutilizables y servidor local.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI usersOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Servicio de usuarios")
                        .version("0.1.0-SNAPSHOT")
                        .description("""
                                Registro de usuarios bancarios del canal WhatsApp (H1). Alta, consulta, \
                                actualización de datos permitidos, cambio de estado y aptitud de vínculo \
                                para consumidores (H3). Sin autenticación JWT, sin Kafka y sin gestión de WhatsApp \
                                en este hito."""))
                .addServersItem(new Server().url("http://localhost:8080").description("Local"))
                .components(new Components()
                        .addExamples("ValidationError", example(error(
                                "VALIDATION_ERROR",
                                "La solicitud tiene datos inválidos",
                                List.of(
                                        field("tipoDocumento"),
                                        field("numeroDocumento"),
                                        field("nombreTratamiento")))))
                        .addExamples("BadRequest", example(error(
                                "BAD_REQUEST", "La solicitud no es válida", List.of())))
                        .addExamples("NotFound", example(error(
                                "NOT_FOUND", "El recurso solicitado no existe", List.of())))
                        .addExamples("ConflictDuplicate", example(error(
                                "CONFLICT",
                                "La operación entra en conflicto con el estado actual",
                                List.of())))
                        .addExamples("ConflictTransition", example(error(
                                "CONFLICT",
                                "La operación entra en conflicto con el estado actual",
                                List.of())))
                        .addExamples("InternalError", example(error(
                                "INTERNAL_ERROR", "Error interno", List.of()))));
    }

    private static Example example(Map<String, Object> value) {
        return new Example().value(value);
    }

    private static Map<String, Object> error(String code, String message, List<Map<String, String>> details) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", code);
        body.put("message", message);
        body.put("details", details);
        body.put("timestamp", "2026-10-15T12:00:00Z");
        return body;
    }

    private static Map<String, String> field(String name) {
        return Map.of("field", name, "message", "El campo tiene un valor inválido");
    }
}
