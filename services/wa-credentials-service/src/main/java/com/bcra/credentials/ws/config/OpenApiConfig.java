package com.bcra.credentials.ws.config;

import java.util.List;
import java.util.Map;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.examples.Example;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Metadatos y ejemplos del contrato real de errores de H2. */
@Configuration
public class OpenApiConfig {

    /** @return especificación de H2 con versión del POM y puerto de la configuración activa. */
    @Bean
    public OpenAPI credentialsOpenApi(@Value("${info.version}") String version,
            @Value("${server.port}") int port) {
        Components components = new Components();
        components.addExamples("ValidationError", error("VALIDATION_ERROR", "La solicitud tiene datos inválidos",
                List.of(Map.of("field", "documentoTipo", "message", "no debe estar vacío"))));
        components.addExamples("ValidationMotivo", error("VALIDATION_ERROR", "La solicitud tiene datos inválidos",
                List.of(Map.of("field", "motivo", "message", "no debe ser nulo"))));
        components.addExamples("ValidationAlcance", error("VALIDATION_ERROR", "La solicitud tiene datos inválidos",
                List.of(Map.of("field", "alcanceValido",
                        "message", "POR_CLIENTE requiere segmento; TODAS no admite segmento"))));
        components.addExamples("ValidationNumeroHistorial", error("VALIDATION_ERROR",
                "La solicitud tiene datos inválidos", List.of(Map.of("field", "consultarHistorial.numero",
                        "message", "debe coincidir con \"\\+[1-9][0-9]{1,14}\""))));
        components.addExamples("ValidationNumeroRevocacion", error("VALIDATION_ERROR",
                "La solicitud tiene datos inválidos", List.of(Map.of("field", "revocar.numero",
                        "message", "debe coincidir con \"\\+[1-9][0-9]{1,14}\""))));
        components.addExamples("BadRequest", error("BAD_REQUEST", "La solicitud no es válida", List.of()));
        components.addExamples("Unauthorized", error("UNAUTHORIZED",
                "La credencial no es válida; se requiere verificar la identidad nuevamente", List.of()));
        components.addExamples("Forbidden", error("FORBIDDEN",
                "La emisión requiere identidad verificada, vínculo activo y cliente activo", List.of()));
        components.addExamples("NotFoundCliente", error("NOT_FOUND", "El cliente no existe", List.of()));
        components.addExamples("NotFoundCredencial", error("NOT_FOUND", "No existe una credencial activa", List.of()));
        components.addExamples("NotFoundHistorial", error("NOT_FOUND",
                "No existe historial de credenciales para el número", List.of()));
        components.addExamples("Conflict", error("CONFLICT", "Conflicto al emitir la credencial", List.of()));
        components.addExamples("ServiceUnavailable", error("SERVICE_UNAVAILABLE",
                "No se pudo validar el cliente", List.of()));
        components.addExamples("InternalError", error("INTERNAL_ERROR", "Error interno", List.of()));

        return new OpenAPI()
                .info(new Info().title("wa-credentials-service").version(version)
                        .description("Servicio de Acceso y Sesión H2: emisión, validación, revocación individual "
                                + "y masiva e historial. API interna para H3 y servicios de productos. "
                                + "Los JWT nunca se muestran al cliente de WhatsApp."))
                .servers(List.of(new Server().url("http://localhost:" + port).description("Local")))
                .components(components);
    }

    private static Example error(String code, String message, List<Map<String, String>> details) {
        return new Example().value(Map.of("code", code, "message", message, "details", details,
                "timestamp", "2026-10-26T12:00:00Z"));
    }
}
