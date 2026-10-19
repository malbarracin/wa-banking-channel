package com.bank.users.exception;

import io.swagger.v3.oas.annotations.media.Schema;

/** Detalle de validación sin el valor rechazado. */
@Schema(description = "Detalle de un campo inválido sin exponer el valor rechazado")
public record FieldError(
        @Schema(description = "Nombre del campo", example = "documentType")
        String field,
        @Schema(description = "Motivo de rechazo", example = "no debe estar vacío")
        String message) {
}
