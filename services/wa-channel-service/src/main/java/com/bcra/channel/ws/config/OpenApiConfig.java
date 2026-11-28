package com.bcra.channel.ws.config;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.bcra.channel.ws.exception.ErrorCode;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.examples.Example;
import io.swagger.v3.oas.models.info.Info;

/**
 * Metadatos OpenAPI y ejemplos reutilizables del contrato de error.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    OpenAPI channelOpenApi() {
        Map<String, Example> examples = ordered(
                Map.entry("BadRequest", errorExample(ErrorCode.BAD_REQUEST, "Solicitud inválida")),
                Map.entry("Forbidden", errorExample(ErrorCode.FORBIDDEN, "Rol interno no autorizado")),
                Map.entry(
                        "NotFound",
                        errorExample(ErrorCode.NOT_FOUND, "No hay historial de auditoría para el número indicado")),
                Map.entry("Conflict", errorExample(ErrorCode.CONFLICT, "El número ya tiene un vínculo activo")),
                Map.entry(
                        "VerificationLocked",
                        errorExample(
                                ErrorCode.VERIFICATION_LOCKED,
                                "Por seguridad, pausamos los intentos de verificación. Comunicate con soporte.")),
                Map.entry("InternalError", errorExample(ErrorCode.INTERNAL_ERROR, "Error interno")));

        return new OpenAPI()
                .info(new Info()
                        .title("wa-channel-service")
                        .version("0.1.0-SNAPSHOT")
                        .description("Vínculo de número WhatsApp con cliente del banco"))
                .components(new Components().examples(examples));
    }

    @SafeVarargs
    private static <K, V> Map<K, V> ordered(Map.Entry<K, V>... entries) {
        Map<K, V> map = new LinkedHashMap<>();
        for (Map.Entry<K, V> entry : entries) {
            map.put(entry.getKey(), entry.getValue());
        }
        return map;
    }

    private static Example errorExample(ErrorCode code, String message) {
        Example example = new Example();
        example.setValue(Map.of(
                "code", code.name(),
                "message", message,
                "details", List.of(),
                "timestamp", "2026-11-27T15:30:00Z"));
        return example;
    }
}
