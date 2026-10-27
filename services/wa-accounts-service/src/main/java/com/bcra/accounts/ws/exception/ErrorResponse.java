package com.bcra.accounts.ws.exception;

import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonFormat;

import io.swagger.v3.oas.annotations.media.Schema;

/** Contrato único de errores; timestamp se serializa en UTC y details nunca es null. */
@Schema(description = "Error uniforme de la API (code, message, details, timestamp)")
public record ErrorResponse(
        @Schema(description = "Código estable", example = "VALIDATION_ERROR") ErrorCode code,
        @Schema(description = "Mensaje legible sin datos sensibles",
                example = "La solicitud tiene datos inválidos") String message,
        @Schema(description = "Detalles de validación; nunca null (lista vacía si no aplica)")
        List<FieldError> details,
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        @Schema(description = "Instante UTC ISO-8601", example = "2026-10-26T12:00:00Z")
        Instant timestamp) {

    public ErrorResponse {
        details = List.copyOf(details);
    }
}
