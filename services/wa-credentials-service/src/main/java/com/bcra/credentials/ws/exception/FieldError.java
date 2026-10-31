package com.bcra.credentials.ws.exception;

import io.swagger.v3.oas.annotations.media.Schema;

/** Detalle de validación sin incluir el valor rechazado. */
@Schema(description = "Detalle de un campo inválido (sin el valor rechazado)")
public record FieldError(
        @Schema(description = "Nombre del campo", example = "documentoTipo") String field,
        @Schema(description = "Motivo del rechazo", example = "no debe estar vacío") String message) {
}
