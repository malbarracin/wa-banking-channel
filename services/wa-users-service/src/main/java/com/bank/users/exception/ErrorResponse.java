package com.bank.users.exception;

import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonFormat;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Contrato uniforme de error; timestamp en UTC y detalles inmutables.
 */
@Schema(description = "Error HTTP uniforme de la API")
public record ErrorResponse(
        @Schema(description = "Código estable del contrato", example = "VALIDATION_ERROR",
                allowableValues = {"VALIDATION_ERROR", "BAD_REQUEST", "NOT_FOUND", "CONFLICT", "INTERNAL_ERROR"})
        ErrorCode code,
        @Schema(description = "Mensaje público sin datos sensibles",
                example = "La solicitud tiene datos inválidos")
        String message,
        @Schema(description = "Detalle por campo; lista vacía si no aplica")
        List<FieldError> details,
        @Schema(description = "Instante UTC ISO-8601", example = "2026-10-15T12:00:00Z")
        @JsonFormat(shape = JsonFormat.Shape.STRING) Instant timestamp) {

    public ErrorResponse {
        details = List.copyOf(details);
    }
}
