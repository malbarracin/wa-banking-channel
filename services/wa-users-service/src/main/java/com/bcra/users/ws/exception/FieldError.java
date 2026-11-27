package com.bcra.users.ws.exception;

import io.swagger.v3.oas.annotations.media.Schema;

/** Detalle de un campo inválido o en conflicto. */
@Schema(description = "Error asociado a un campo")
public record FieldError(
        @Schema(description = "Nombre del campo", example = "contactEmail",
                requiredMode = Schema.RequiredMode.REQUIRED)
        String field,
        @Schema(description = "Motivo del rechazo", example = "must be a well-formed email address",
                requiredMode = Schema.RequiredMode.REQUIRED)
        String message) {
}
