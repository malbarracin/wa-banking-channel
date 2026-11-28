package com.bcra.users.ws.exception;

import java.time.Instant;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

/** Respuesta de error única de la API. */
@Schema(description = "Error con código estable, mensaje, detalles y marca de tiempo UTC")
public record ErrorResponse(
        @Schema(description = "Código del error", example = "VALIDATION_ERROR",
                requiredMode = Schema.RequiredMode.REQUIRED)
        ErrorCode code,
        @Schema(description = "Mensaje legible sin datos internos", example = "Validation failed",
                requiredMode = Schema.RequiredMode.REQUIRED)
        String message,
        @Schema(description = "Detalle por campo; lista vacía si no aplica",
                requiredMode = Schema.RequiredMode.REQUIRED)
        List<FieldError> details,
        @Schema(description = "Instante UTC del error", example = "2026-11-27T12:00:00Z",
                requiredMode = Schema.RequiredMode.REQUIRED)
        Instant timestamp) {

    public ErrorResponse {
        details = List.copyOf(details);
    }
}
