package com.bcra.ws.users.exception;

import java.time.Instant;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

/** Contrato único de errores de la API. */
@Schema(description = "Error de la API sin datos internos ni stacktrace")
public record ErrorResponse(
        @Schema(description = "Código estable del contrato", example = "NOT_FOUND",
                allowableValues = {
                        "VALIDATION_ERROR", "BAD_REQUEST", "NOT_FOUND", "CONFLICT", "INTERNAL_ERROR"})
        ErrorCode code,
        @Schema(description = "Mensaje orientado al cliente", example = "User not found")
        String message,
        @Schema(description = "Detalles por campo; nunca null (lista vacía si no aplica)")
        List<FieldError> details,
        @Schema(description = "Instante UTC ISO-8601", example = "2026-10-19T12:00:00Z")
        Instant timestamp) {

    public ErrorResponse {
        details = details == null ? List.of() : List.copyOf(details);
        timestamp = timestamp == null ? Instant.now() : timestamp;
    }

    public static ErrorResponse of(ErrorCode code, String message) {
        return of(code, message, List.of());
    }

    public static ErrorResponse of(ErrorCode code, String message, List<FieldError> details) {
        return new ErrorResponse(code, message, details, Instant.now());
    }
}
