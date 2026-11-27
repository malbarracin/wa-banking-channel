package com.bcra.users.ws.config;

import java.util.List;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.examples.Example;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Schema;
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
                        .addSchemas("ErrorResponse", errorResponseSchema())
                        .addExamples("ValidationError", example(
                                "{\"code\":\"VALIDATION_ERROR\",\"message\":\"Validation failed\","
                                        + "\"details\":[{\"field\":\"contactEmail\",\"message\":\"must be a well-formed email address\"}],"
                                        + "\"timestamp\":\"" + ERROR_EXAMPLE_TIMESTAMP + "\"}"))
                        .addExamples("BadRequest", example(
                                "{\"code\":\"BAD_REQUEST\",\"message\":\"Invalid request\",\"details\":[],"
                                        + "\"timestamp\":\"" + ERROR_EXAMPLE_TIMESTAMP + "\"}"))
                        .addExamples("NotFound", example(
                                "{\"code\":\"NOT_FOUND\",\"message\":\"No existe el usuario solicitado\","
                                        + "\"details\":[],\"timestamp\":\"" + ERROR_EXAMPLE_TIMESTAMP + "\"}"))
                        .addExamples("Duplicate", example(
                                "{\"code\":\"CONFLICT\",\"message\":\"DUPLICADO\","
                                        + "\"details\":[{\"field\":\"uniquenessValue\",\"message\":\"DUPLICADO\"}],"
                                        + "\"timestamp\":\"" + ERROR_EXAMPLE_TIMESTAMP + "\"}"))
                        .addExamples("InvalidTransition", example(
                                "{\"code\":\"CONFLICT\",\"message\":\"Transición de estado no permitida\","
                                        + "\"details\":[{\"field\":\"previousStatus\",\"message\":\"INACTIVE\"},"
                                        + "{\"field\":\"newStatus\",\"message\":\"ACTIVE\"},"
                                        + "{\"field\":\"reason\",\"message\":\"TRANSICION_NO_PERMITIDA\"}],"
                                        + "\"timestamp\":\"" + ERROR_EXAMPLE_TIMESTAMP + "\"}"))
                        .addExamples("InternalError", example(
                                "{\"code\":\"INTERNAL_ERROR\",\"message\":\"Internal error\",\"details\":[],"
                                        + "\"timestamp\":\"" + ERROR_EXAMPLE_TIMESTAMP + "\"}")));
    }

    private static Schema<?> errorResponseSchema() {
        Schema<?> fieldError = new Schema<>()
                .type("object")
                .addProperty("field", new Schema<>().type("string").example("contactEmail"))
                .addProperty("message", new Schema<>().type("string").example("must be a well-formed email address"));
        return new Schema<>()
                .type("object")
                .addProperty("code", new Schema<>().type("string")
                        .example("VALIDATION_ERROR")
                        .description("Código estable del error"))
                .addProperty("message", new Schema<>().type("string").example("Validation failed"))
                .addProperty("details", new Schema<>().type("array").items(fieldError))
                .addProperty("timestamp", new Schema<>().type("string").format("date-time")
                        .example(ERROR_EXAMPLE_TIMESTAMP));
    }

    private static Example example(String value) {
        return new Example().value(value);
    }
}
