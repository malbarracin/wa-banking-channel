package com.bcra.cards.ws.exception;

import io.swagger.v3.oas.annotations.media.Schema;

/** Detalle de validación sin incluir el valor rechazado. */
@Schema(description = "Detalle de un campo inválido (sin el valor rechazado)")
public record FieldError(
        @Schema(description = "Nombre del campo", example = "nombre") String field,
        @Schema(description = "Motivo del rechazo", example = "El nombre es obligatorio") String message) {
}
