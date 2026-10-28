package com.bank.users.config;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.examples.Example;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.servers.Server;

/**
 * Configuración compartida de OpenAPI/Swagger para wa-users-service.
 * <p>
 * Define metadatos del servicio (título, versión, descripción), el servidor local de desarrollo
 * y ejemplos de error reutilizables en {@code components.examples}. Los controllers referencian
 * estos ejemplos desde {@code @ApiResponse} mediante
 * {@code @ExampleObject(ref = "#/components/examples/...")}.
 * </p>
 * <p>
 * La UI de Swagger (/swagger-ui.html) se desactiva en producción mediante la propiedad
 * {@code springdoc.swagger-ui.enabled=false} en {@code application-prod.yml}.
 * </p>
 */
@Configuration
public class OpenApiConfig {

    /** Versión del servicio expuesta en la documentación OpenAPI. */
    private static final String SERVICE_VERSION = "0.1.0-SNAPSHOT";
    /** Marca de tiempo de ejemplo ISO-8601 en UTC. */
    private static final String TIMESTAMP_EXAMPLE = "2026-10-05T14:30:00Z";

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Bean principal de OpenAPI: información del servicio, servidor local y ejemplos de error
     * reutilizables para el contrato {@code {code, message, details, timestamp}}.
     */
    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("wa-users-service")
                        .version(SERVICE_VERSION)
                        .description("Servicio de usuarios del canal WhatsApp del banco. "
                                + "Administra altas, consultas, actualización de datos permitidos y "
                                + "transiciones de estado operativo (activo, suspendido, baja lógica). "
                                + "La API está versionada bajo /api/v1. "
                                + "Fuera de alcance H1: vínculo WhatsApp (H3), sesiones (H2) y productos.")
                        .contact(new Contact()
                                .name("Equipo backend")
                                .url("https://app.notion.com/p/H1-Servicio-de-Usuarios-36875bb08ca78197a853e85505bf49a5")))
                .servers(List.of(new Server()
                        .url("http://localhost:8080")
                        .description("Servidor local de desarrollo")))
                .components(errorComponents());
    }

    /** Schemas y ejemplos de error reutilizables, uno por código del contrato. */
    private Components errorComponents() {
        Components components = new Components();
        components.addSchemas("ErrorResponse", errorResponseSchema());
        components.addExamples("ValidationError", example(
                "Respuesta de validación con campos inválidos",
                "{\"code\":\"VALIDATION_ERROR\",\"message\":\"La solicitud tiene datos inválidos\","
                        + "\"details\":[{\"field\":\"documentType\",\"message\":\"no debe estar vacío\"}],"
                        + "\"timestamp\":\"" + TIMESTAMP_EXAMPLE + "\"}"));
        components.addExamples("BadRequest", example(
                "Solicitud inválida (parámetros, formato o campo no permitido)",
                "{\"code\":\"BAD_REQUEST\",\"message\":\"Solicitud inválida\","
                        + "\"details\":[],\"timestamp\":\"" + TIMESTAMP_EXAMPLE + "\"}"));
        components.addExamples("NotFound", example(
                "Recurso no encontrado",
                "{\"code\":\"NOT_FOUND\",\"message\":\"No existe el usuario solicitado\","
                        + "\"details\":[],\"timestamp\":\"" + TIMESTAMP_EXAMPLE + "\"}"));
        components.addExamples("Duplicate", example(
                "Conflicto de dominio: documento duplicado",
                "{\"code\":\"CONFLICT\",\"message\":\"Ya existe un usuario con ese documento\","
                        + "\"details\":[],\"timestamp\":\"" + TIMESTAMP_EXAMPLE + "\"}"));
        components.addExamples("InvalidTransition", example(
                "Conflicto de dominio: transición de estado no permitida",
                "{\"code\":\"CONFLICT\",\"message\":\"La transición de estado solicitada no está permitida\","
                        + "\"details\":[],\"timestamp\":\"" + TIMESTAMP_EXAMPLE + "\"}"));
        components.addExamples("Conflict", example(
                "Conflicto de dominio (transición inválida, baja lógica)",
                "{\"code\":\"CONFLICT\",\"message\":\"No se pueden actualizar datos de un usuario dado de baja lógica\","
                        + "\"details\":[],\"timestamp\":\"" + TIMESTAMP_EXAMPLE + "\"}"));
        components.addExamples("InternalError", example(
                "Error interno inesperado",
                "{\"code\":\"INTERNAL_ERROR\",\"message\":\"Error interno\","
                        + "\"details\":[],\"timestamp\":\"" + TIMESTAMP_EXAMPLE + "\"}"));
        return components;
    }

    /** Schema del contrato de error público. */
    @SuppressWarnings("rawtypes")
    private Schema errorResponseSchema() {
        Schema detailsItem = new Schema<>()
                .type("object")
                .addProperty("field", new Schema<>().type("string").example("documentType"))
                .addProperty("message", new Schema<>().type("string").example("no debe estar vacío"));
        return new Schema<>()
                .type("object")
                .description("Contrato único de error HTTP")
                .addProperty("code", new Schema<>().type("string")
                        .example("VALIDATION_ERROR")
                        ._enum(List.of("VALIDATION_ERROR", "BAD_REQUEST", "NOT_FOUND", "CONFLICT", "INTERNAL_ERROR")))
                .addProperty("message", new Schema<>().type("string")
                        .example("La solicitud tiene datos inválidos"))
                .addProperty("details", new Schema<>().type("array").items(detailsItem))
                .addProperty("timestamp", new Schema<>().type("string").format("date-time")
                        .example(TIMESTAMP_EXAMPLE));
    }

    /** Crea un ejemplo con descripción y valor JSON parseado (objeto, no string escapado). */
    private Example example(String description, String jsonValue) {
        try {
            Map<String, Object> value = objectMapper.readValue(jsonValue, new TypeReference<>() {
            });
            return new Example().description(description).value(value);
        } catch (JsonProcessingException exception) {
            Map<String, Object> fallback = new LinkedHashMap<>();
            fallback.put("raw", jsonValue);
            return new Example().description(description).value(fallback);
        }
    }
}
