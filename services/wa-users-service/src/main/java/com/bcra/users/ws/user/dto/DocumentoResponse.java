package com.bcra.users.ws.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** Documento identificador del usuario; el catálogo de tipos es abierto. */
@Schema(description = "Documento del usuario en respuestas")
public record DocumentoResponse(
        @Schema(description = "Tipo de documento", example = "DNI") String tipo,
        @Schema(description = "Número de documento", example = "30111222") String numero) {
}
