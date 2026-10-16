package com.bank.users.exception;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Campo inválido y motivo público, sin el valor rechazado.
 */
@Schema(description = "Detalle de validación por campo")
public record FieldError(
        @Schema(description = "Nombre del campo", example = "tipoDocumento")
        String field,
        @Schema(description = "Motivo público sin el valor rechazado",
                example = "El campo tiene un valor inválido")
        String message) {
}
