package com.bcra.credentials.ws.credencial.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** JWT presentado por un servicio de productos; nunca debe incluirse en logs. */
@Schema(description = "JWT presentado por un servicio de productos; nunca debe incluirse en logs.")
public record ValidarCredencialRequest(
        @Schema(description = "JWT de sesión interno; usar el valor devuelto por emitir, nunca mostrarlo al cliente",
                example = "JWT_DEVUELTO_POR_EMITIR")
        String credencial) {

    @Override
    public String toString() {
        return "ValidarCredencialRequest[credencial=PROTEGIDA]";
    }
}
