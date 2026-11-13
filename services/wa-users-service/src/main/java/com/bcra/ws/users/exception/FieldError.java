package com.bcra.ws.users.exception;

import io.swagger.v3.oas.annotations.media.Schema;

/** Detalle de validación o conflicto sin el valor recibido. */
@Schema(description = "Detalle de error asociado a un campo")
public record FieldError(
        @Schema(description = "Nombre del campo", example = "email")
        String field,
        @Schema(description = "Motivo", example = "email must be valid")
        String message) {
}
