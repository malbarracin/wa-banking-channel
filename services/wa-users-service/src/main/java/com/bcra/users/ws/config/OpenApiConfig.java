package com.bcra.users.ws.config;

import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.examples.Example;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.servers.Server;

/**
 * Contrato OpenAPI del servicio: metadatos, servidor local y ejemplos reutilizables del error único.
 */
@Configuration
public class OpenApiConfig {

    private static final String ERROR_JSON = "application/json";

    @Bean
    public OpenAPI waUsersOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("wa-users-service")
                        .version("0.1.0-SNAPSHOT")
                        .description("""
                                Servicio de usuarios bancarios (H1): alta, consulta por documento, \
                                actualización de datos permitidos y cambio de estado operativo. \
                                No gestiona WhatsApp, verificación de identidad ni credenciales de sesión."""))
                .servers(List.of(new Server().url("http://localhost:8080").description("Local")))
                .components(new Components()
                        .addSchemas("ErrorResponse", errorSchema())
                        .addExamples("ValidationError", example("""
                                {
                                  "code": "VALIDATION_ERROR",
                                  "message": "La solicitud tiene datos inválidos",
                                  "details": [
                                    { "field": "nombre", "message": "El nombre es obligatorio" }
                                  ],
                                  "timestamp": "2026-10-26T12:00:00Z"
                                }
                                """))
                        .addExamples("BadRequest", example("""
                                {
                                  "code": "BAD_REQUEST",
                                  "message": "La solicitud no es válida",
                                  "details": [],
                                  "timestamp": "2026-10-26T12:00:00Z"
                                }
                                """))
                        .addExamples("NonEditableField", example("""
                                {
                                  "code": "BAD_REQUEST",
                                  "message": "El campo no es editable por este servicio",
                                  "details": [],
                                  "timestamp": "2026-10-26T12:00:00Z"
                                }
                                """))
                        .addExamples("InvalidTransition", example("""
                                {
                                  "code": "BAD_REQUEST",
                                  "message": "La solicitud no es válida",
                                  "details": [],
                                  "timestamp": "2026-10-26T12:00:00Z"
                                }
                                """))
                        .addExamples("NotFound", example("""
                                {
                                  "code": "NOT_FOUND",
                                  "message": "Usuario no encontrado",
                                  "details": [],
                                  "timestamp": "2026-10-26T12:00:00Z"
                                }
                                """))
                        .addExamples("Duplicate", example("""
                                {
                                  "code": "CONFLICT",
                                  "message": "Ya existe un usuario con ese documento (duplicado)",
                                  "details": [],
                                  "timestamp": "2026-10-26T12:00:00Z"
                                }
                                """))
                        .addExamples("InternalError", example("""
                                {
                                  "code": "INTERNAL_ERROR",
                                  "message": "Error interno",
                                  "details": [],
                                  "timestamp": "2026-10-26T12:00:00Z"
                                }
                                """))
                        .addResponses("ValidationError", errorResponse("Datos inválidos", "ValidationError"))
                        .addResponses("BadRequest", errorResponse("Solicitud no válida", "BadRequest"))
                        .addResponses("NonEditableField",
                                errorResponse("Campo no editable", "NonEditableField"))
                        .addResponses("InvalidTransition",
                                errorResponse("Transición de estado no soportada", "InvalidTransition"))
                        .addResponses("NotFound", errorResponse("Usuario no encontrado", "NotFound"))
                        .addResponses("Duplicate", errorResponse("Documento duplicado", "Duplicate"))
                        .addResponses("InternalError", errorResponse("Error interno", "InternalError")));
    }

    private static Schema<?> errorSchema() {
        Schema<Object> schema = new Schema<>();
        schema.setType("object");
        schema.addProperty("code", new Schema<>().type("string")
                .description("Código estable del contrato")
                .example("VALIDATION_ERROR"));
        schema.addProperty("message", new Schema<>().type("string")
                .description("Mensaje legible sin datos sensibles")
                .example("La solicitud tiene datos inválidos"));
        schema.addProperty("details", new Schema<>().type("array")
                .description("Detalles de validación; lista vacía si no aplica")
                .items(new Schema<>().type("object")
                        .addProperty("field", new Schema<>().type("string").example("nombre"))
                        .addProperty("message", new Schema<>().type("string")
                                .example("El nombre es obligatorio"))));
        schema.addProperty("timestamp", new Schema<>().type("string").format("date-time")
                .description("Instante UTC ISO-8601")
                .example("2026-10-26T12:00:00Z"));
        return schema;
    }

    private static Example example(String value) {
        Example example = new Example();
        example.setValue(value.strip());
        return example;
    }

    private static ApiResponse errorResponse(String description, String exampleName) {
        MediaType mediaType = new MediaType()
                .schema(new Schema<>().$ref("#/components/schemas/ErrorResponse"))
                .addExamples(exampleName, new Example().$ref("#/components/examples/" + exampleName));
        return new ApiResponse()
                .description(description)
                .content(new Content().addMediaType(ERROR_JSON, mediaType));
    }
}
