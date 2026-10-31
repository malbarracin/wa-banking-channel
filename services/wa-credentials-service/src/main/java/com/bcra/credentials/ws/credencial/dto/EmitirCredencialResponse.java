package com.bcra.credentials.ws.credencial.dto;

import java.time.Instant;

import io.swagger.v3.oas.annotations.media.Schema;

/** Credencial destinada exclusivamente al canal H3, nunca a mensajes visibles por el cliente final. */
@Schema(description =
        "Credencial destinada exclusivamente al canal H3, nunca a mensajes "
        + "visibles por el cliente final.")
public record EmitirCredencialResponse(
        @Schema(description = "JWT de sesión interno; usar el valor devuelto por emitir, nunca mostrarlo al cliente",
                example = "JWT_DEVUELTO_POR_EMITIR")
        String credencial,
        @Schema(description = "Vencimiento UTC en formato ISO-8601",
                example = "2026-10-27T12:00:00Z")
        Instant fechaExpiracion) {

    @Override
    public String toString() {
        return "EmitirCredencialResponse[protegido]";
    }
}
