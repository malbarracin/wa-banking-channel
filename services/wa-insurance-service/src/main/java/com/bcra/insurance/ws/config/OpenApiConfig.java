package com.bcra.insurance.ws.config;

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
    public OpenAPI waInsuranceOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("wa-insurance-service")
                        .version("0.1.0-SNAPSHOT")
                        .description("""
                                Servicio de seguros bancarios (H6): consulta de pólizas y coberturas, \
                                denuncia de siniestros y seguimiento de casos con documentación pendiente. \
                                Todas las operaciones requieren credencial de sesión de H2 en X-Credencial-Sesion."""))
                .servers(List.of(new Server().url("http://localhost:8085").description("Local")))
                .components(new Components()
                        .addSchemas("ErrorResponse", errorSchema())
                        .addExamples("ValidationError", example("""
                                {
                                  "code": "VALIDATION_ERROR",
                                  "message": "La solicitud tiene datos inválidos",
                                  "details": [
                                    { "field": "descripcion", "message": "no debe estar vacío" }
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
                        .addExamples("Unauthorized", example("""
                                {
                                  "code": "UNAUTHORIZED",
                                  "message": "Verificá tu identidad para consultar tus seguros.",
                                  "details": [],
                                  "timestamp": "2026-10-26T12:00:00Z"
                                }
                                """))
                        .addExamples("Forbidden", example("""
                                {
                                  "code": "FORBIDDEN",
                                  "message": "Consultá al titular o acudí a un canal formal para operar sobre esta póliza.",
                                  "details": [],
                                  "timestamp": "2026-10-26T12:00:00Z"
                                }
                                """))
                        .addExamples("NotFound", example("""
                                {
                                  "code": "NOT_FOUND",
                                  "message": "La póliza no existe.",
                                  "details": [],
                                  "timestamp": "2026-10-26T12:00:00Z"
                                }
                                """))
                        .addExamples("Duplicate", example("""
                                {
                                  "code": "CONFLICT",
                                  "message": "Ya existe una denuncia abierta para esta póliza y tipo de evento.",
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
                        .addResponses("Unauthorized", errorResponse("No autorizado", "Unauthorized"))
                        .addResponses("Forbidden", errorResponse("Prohibido", "Forbidden"))
                        .addResponses("NotFound", errorResponse("No encontrado", "NotFound"))
                        .addResponses("Duplicate", errorResponse("Conflicto / duplicado", "Duplicate"))
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
                        .addProperty("field", new Schema<>().type("string").example("descripcion"))
                        .addProperty("message", new Schema<>().type("string")
                                .example("no debe estar vacío"))));
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
