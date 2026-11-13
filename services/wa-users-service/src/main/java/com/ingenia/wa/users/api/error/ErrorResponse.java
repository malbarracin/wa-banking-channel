package com.ingenia.wa.users.api.error;

import java.time.Instant;
import java.util.Collections;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Contrato unificado de error de la API.
 *
 * @author Ingenia
 * @since 2026-10-19
 * Contacto: marcelo.albarracin@ingenia.la
 */
@Schema(description = "Contrato unificado de error (sin stacktrace)")
public record ErrorResponse(
        @Schema(
                description = "Código de error del contrato",
                example = "NOT_FOUND",
                allowableValues = {"NOT_FOUND", "VALIDATION_ERROR", "BAD_REQUEST", "INTERNAL_ERROR"},
                requiredMode = Schema.RequiredMode.REQUIRED)
        String code,

        @Schema(
                description = "Mensaje legible del error",
                example = "User not found: 661f0a1b2c3d4e5f67890123",
                requiredMode = Schema.RequiredMode.REQUIRED)
        String message,

        @Schema(
                description = "Detalles opcionales (p. ej. field + message de validación)",
                example = "[]",
                requiredMode = Schema.RequiredMode.REQUIRED)
        List<Object> details,

        @Schema(
                description = "Timestamp UTC ISO-8601",
                example = "2026-10-19T12:00:00Z",
                requiredMode = Schema.RequiredMode.REQUIRED)
        Instant timestamp
) {

    public ErrorResponse {
        details = details == null ? Collections.emptyList() : List.copyOf(details);
        timestamp = timestamp == null ? Instant.now() : timestamp;
    }

    public static ErrorResponse of(String code, String message) {
        return new ErrorResponse(code, message, Collections.emptyList(), Instant.now());
    }

    public static ErrorResponse of(String code, String message, List<Object> details) {
        return new ErrorResponse(code, message, details, Instant.now());
    }
}
