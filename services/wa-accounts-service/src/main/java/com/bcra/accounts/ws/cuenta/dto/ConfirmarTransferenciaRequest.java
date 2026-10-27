package com.bcra.accounts.ws.cuenta.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** Confirmación literal del cliente; el identificador se recibe en el path. */
public record ConfirmarTransferenciaRequest(
        @Schema(description = "Enviar CONFIRMAR literal; otro valor reinicia la secuencia y devuelve 409",
                example = "CONFIRMAR")
        String confirmacion) {
}
