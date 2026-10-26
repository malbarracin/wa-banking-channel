package com.bcra.channel.ws.config;

import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.examples.Example;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;

/**
 * Contrato OpenAPI del servicio: metadatos, servidor local y ejemplos reutilizables del error único.
 * Los ejemplos coinciden con los mensajes reales de {@code GlobalExceptionHandler} y de las excepciones
 * de dominio del módulo {@code vinculo}.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI waChannelOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("wa-channel-service")
                        .version("0.1.0-SNAPSHOT")
                        .description("""
                                Servicio Canal WhatsApp (H3): aceptación de términos, verificación de identidad, \
                                vinculación y re-vinculación, estado anti-duplicados, perfil y preferencias, \
                                bloqueo, desvinculación e historial para soporte. No emite credenciales de sesión \
                                (H2) ni gestiona mensajes de WhatsApp (H0)."""))
                .servers(List.of(new Server().url("http://localhost:8081").description("Local")))
                .components(new Components()
                        .addExamples("ValidationError", example("""
                                {
                                  "code": "VALIDATION_ERROR",
                                  "message": "La solicitud tiene datos inválidos",
                                  "details": [
                                    { "field": "numeroWhatsapp", "message": "El número de WhatsApp es obligatorio" }
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
                        .addExamples("UnknownProperty", example("""
                                {
                                  "code": "VALIDATION_ERROR",
                                  "message": "La solicitud tiene propiedades no permitidas",
                                  "details": [],
                                  "timestamp": "2026-10-26T12:00:00Z"
                                }
                                """))
                        .addExamples("NotFoundVinculo", example("""
                                {
                                  "code": "NOT_FOUND",
                                  "message": "No existe un vínculo registrado para el número consultado",
                                  "details": [],
                                  "timestamp": "2026-10-26T12:00:00Z"
                                }
                                """))
                        .addExamples("NotFoundTerminos", example("""
                                {
                                  "code": "NOT_FOUND",
                                  "message": "Debe aceptar los términos antes de verificar su identidad",
                                  "details": [],
                                  "timestamp": "2026-10-26T12:00:00Z"
                                }
                                """))
                        .addExamples("NotFoundCliente", example("""
                                {
                                  "code": "NOT_FOUND",
                                  "message": "Para operar por WhatsApp primero debe ser cliente del banco por otro canal",
                                  "details": [],
                                  "timestamp": "2026-10-26T12:00:00Z"
                                }
                                """))
                        .addExamples("ConflictClienteNoHabilitado", example("""
                                {
                                  "code": "CONFLICT",
                                  "message": "El cliente no está habilitado para operar por este canal",
                                  "details": [],
                                  "timestamp": "2026-10-26T12:00:00Z"
                                }
                                """))
                        .addExamples("ConflictNumeroVinculado", example("""
                                {
                                  "code": "CONFLICT",
                                  "message": "El número ya está vinculado. Contacte a soporte",
                                  "details": [],
                                  "timestamp": "2026-10-26T12:00:00Z"
                                }
                                """))
                        .addExamples("ConflictEstado", example("""
                                {
                                  "code": "CONFLICT",
                                  "message": "El vínculo no admite la operación en su estado actual",
                                  "details": [],
                                  "timestamp": "2026-10-26T12:00:00Z"
                                }
                                """))
                        .addExamples("ConflictNoActivo", example("""
                                {
                                  "code": "CONFLICT",
                                  "message": "El vínculo no está activo para esta operación",
                                  "details": [],
                                  "timestamp": "2026-10-26T12:00:00Z"
                                }
                                """))
                        .addExamples("Unauthorized", example("""
                                {
                                  "code": "UNAUTHORIZED",
                                  "message": "Se requiere una credencial válida",
                                  "details": [],
                                  "timestamp": "2026-10-26T12:00:00Z"
                                }
                                """))
                        .addExamples("Locked", example("""
                                {
                                  "code": "LOCKED",
                                  "message": "Verificación pausada temporalmente. Contacte a soporte",
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
                                """)));
    }

    private static Example example(String value) {
        Example example = new Example();
        example.setValue(value.strip());
        return example;
    }
}
