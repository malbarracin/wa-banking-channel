package com.bcra.cards.ws.config;

import com.bcra.cards.ws.exception.ErrorResponse;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.examples.Example;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.servers.Server;
import java.util.List;
import java.util.Map;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Contrato HTTP y ejemplos reutilizables del servicio de tarjetas. */
@Configuration
public class OpenApiConfig {

    /** Define identidad, autenticación por header y respuestas del contrato de errores. */
    @Bean
    public OpenAPI tarjetasOpenApi(@Value("${server.port:8086}") int port) {
        Components components = new Components();
        ModelConverters.getInstance().readAll(ErrorResponse.class).forEach(components::addSchemas);
        components.addSecuritySchemes("credencialSesion", new SecurityScheme()
                .type(SecurityScheme.Type.APIKEY).in(SecurityScheme.In.HEADER).name("X-Credencial-Sesion")
                .description("JWT de sesión validado con H2; vínculo activo requerido"));
        agregarError(components, "400", "BAD_REQUEST", "La solicitud no es válida");
        agregarError(components, "401", "UNAUTHORIZED", "Credencial no válida");
        agregarError(components, "403", "FORBIDDEN", "Acceso no permitido");
        agregarError(components, "404", "NOT_FOUND", "No se encontró la tarjeta");
        agregarError(components, "409", "CONFLICT", "El estado de la tarjeta no permite aplicar el bloqueo");
        agregarError(components, "500", "INTERNAL_ERROR", "Error interno");
        agregarConflicto(components, "Irreversible",
                "El bloqueo definitivo no se puede revertir por chat. Contactá los canales formales del banco");
        agregarConflicto(components, "DobleConfirmacion",
                "El bloqueo definitivo es irreversible por WhatsApp. "
                        + "Para continuar, confirme dos veces con CONFIRMAR e indique ROBO o EXTRAVIO.");
        agregarConflicto(components, "Confirmacion",
                "Se requiere confirmación explícita para continuar");
        components.addExamples("ValidationError", new Example().value(Map.of(
                "code", "VALIDATION_ERROR", "message", "La solicitud tiene datos inválidos",
                "details", List.of(Map.of("field", "tarjetaId", "message", "no debe ser nulo")),
                "timestamp", "2026-10-30T12:00:00Z")));
        components.getResponses().get("400").getContent().get("application/json")
                .addExamples("ValidationError", new Example().$ref("#/components/examples/ValidationError"));
        return new OpenAPI().info(new Info().title("WA Cards Service").version("0.1.0-SNAPSHOT")
                .description("Servicio de tarjetas del canal bancario: consultas, bloqueos y avisos de viaje."))
                .servers(List.of(new Server().url("http://localhost:" + port).description("Local")))
                .components(components).addSecurityItem(new SecurityRequirement().addList("credencialSesion"));
    }

    /** Agrega ejemplos de éxito conservando los esquemas inferidos de los DTOs. */
    @Bean
    public OpenApiCustomizer ejemplosDeExito() {
        return api -> api.getPaths().values().forEach(path -> path.readOperations().forEach(operation -> {
            String id = operation.getOperationId();
            String ejemplo = switch (id) {
                case "listar" -> """
                        [
                          {
                            "id": "11111111-1111-4111-8111-111111111111",
                            "ultimosCuatro": "1234",
                            "marca": "VISA",
                            "tipo": "CREDITO",
                            "estado": "ACTIVA"
                          }
                        ]
                        """;
                case "listar_1" -> """
                        {
                          "consumos": [
                            {
                              "fecha": "2026-10-30T12:00:00Z",
                              "comercio": "Comercio de prueba",
                              "monto": 1250.5,
                              "moneda": "ARS",
                              "descripcion": "Compra",
                              "referencia": "COMPRA-001"
                            }
                          ],
                          "tieneMas": false,
                          "cursor": null
                        }
                        """;
                case "registrar" -> """
                        {
                          "tarjetaId": "11111111-1111-4111-8111-111111111111",
                          "pais": "España",
                          "region": "Madrid",
                          "fechaDesde": "2026-12-01",
                          "fechaHasta": "2026-12-15",
                          "mensajeConfirmacion": "Aviso de viaje registrado y confirmado para España / Madrid, \
                          desde 2026-12-01 hasta 2026-12-15. Usá canales oficiales y no compartas tus claves. \
                          Siguen aplicando las políticas internacionales del emisor y los límites del banco."
                        }
                        """;
                case "bloquearDefinitivo" -> """
                        {
                          "tarjeta": {
                            "id": "11111111-1111-4111-8111-111111111111",
                            "ultimosCuatro": "1234",
                            "marca": "VISA",
                            "tipo": "CREDITO",
                            "estado": "BLOQUEO_DEFINITIVO"
                          },
                          "mensaje": "Bloqueo definitivo aplicado, irreversible por WhatsApp. Solicite la \
                          reposición por los canales oficiales del banco."
                        }
                        """;
                case "bloquearTemporal" -> """
                        {
                          "tarjetaId": "11111111-1111-4111-8111-111111111111",
                          "estado": "BLOQUEO_TEMPORAL",
                          "mensaje": "Tarjeta bloqueada temporalmente. Para desbloquearla, indicá DESBLOQUEAR."
                        }
                        """;
                case "desbloquearTemporal" -> """
                        {
                          "id": "11111111-1111-4111-8111-111111111111",
                          "ultimosCuatro": "1234",
                          "marca": "VISA",
                          "tipo": "CREDITO",
                          "estado": "ACTIVA"
                        }
                        """;
                default -> null;
            };
            if (ejemplo != null) {
                operation.getResponses().forEach((status, response) -> {
                    if (status.startsWith("2") && response.getContent() != null) {
                        response.getContent().values().forEach(media ->
                                media.addExamples("Exito", new Example().value(parsearEjemplo(ejemplo))));
                    }
                });
            }
        }));
    }

    private com.fasterxml.jackson.databind.JsonNode parsearEjemplo(String json) {
        try {
            return io.swagger.v3.core.util.Json.mapper().readTree(json);
        } catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
            throw new IllegalStateException("Ejemplo OpenAPI inválido", exception);
        }
    }

    private void agregarConflicto(Components components, String name, String message) {
        components.addExamples(name, new Example().value(Map.of(
                "code", "CONFLICT", "message", message, "details", List.of(),
                "timestamp", "2026-10-30T12:00:00Z")));
        components.getResponses().get("409").getContent().get("application/json")
                .addExamples(name, new Example().$ref("#/components/examples/" + name));
    }

    private void agregarError(Components components, String status, String code, String message) {
        components.addExamples(code, new Example().value(Map.of(
                "code", code, "message", message, "details", List.of(), "timestamp", "2026-10-30T12:00:00Z")));
        components.addResponses(status, new ApiResponse().description(code + ": " + message)
                .content(new Content().addMediaType("application/json", new MediaType()
                        .schema(new Schema<>().$ref("#/components/schemas/ErrorResponse"))
                        .addExamples(code, new Example().$ref("#/components/examples/" + code)))));
    }
}
