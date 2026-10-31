package com.bcra.credentials.ws.credencial.dto;

import java.time.Instant;

import io.swagger.v3.oas.annotations.media.Schema;

/** Identidad de la credencial vigente y vencimiento UTC. */
@Schema(description = "Identidad de la credencial vigente y vencimiento UTC.")
public record ValidarCredencialResponse(
        @Schema(description = "Indica que la credencial permite operar; true en una respuesta exitosa",
                example = "true")
        boolean vigente,
        @Schema(description = "Número de WhatsApp en formato E.164",
                example = "+5491111111111")
        String numeroWhatsapp,
        @Schema(description = "Tipo documental del cliente en H1",
                example = "DNI")
        String documentoTipo,
        @Schema(description = "Número documental del cliente en H1",
                example = "12345678")
        String documentoNumero,
        @Schema(description = "Vencimiento UTC en formato ISO-8601",
                example = "2026-10-27T12:00:00Z")
        Instant fechaExpiracion) {

    @Override
    public String toString() {
        return "ValidarCredencialResponse[vigente=" + vigente + "]";
    }
}
