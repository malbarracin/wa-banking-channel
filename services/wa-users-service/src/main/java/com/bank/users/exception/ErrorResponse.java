package com.bank.users.exception;

import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonFormat;

import io.swagger.v3.oas.annotations.media.Schema;

/** Respuesta común de error con fecha UTC y detalles inmutables. */
@Schema(description = "Contrato único de error HTTP de la API")
public record ErrorResponse(
        @Schema(description = "Código estable del error", example = "VALIDATION_ERROR")
        ErrorCode code,
        @Schema(description = "Mensaje público seguro", example = "La solicitud tiene datos inválidos")
        String message,
        @Schema(description = "Detalle por campo; nunca null (puede ser lista vacía)")
        List<FieldError> details,
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        @Schema(description = "Marca de tiempo UTC ISO-8601", example = "2026-10-05T14:30:00Z")
        Instant timestamp) {

    public ErrorResponse {
        details = List.copyOf(details);
    }
}
