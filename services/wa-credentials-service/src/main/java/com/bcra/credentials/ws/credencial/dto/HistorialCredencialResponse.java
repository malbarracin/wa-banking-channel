package com.bcra.credentials.ws.credencial.dto;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

import com.bcra.credentials.ws.credencial.entity.CredencialEstado;

/** Estado efectivo de la última credencial y eventos del número, del más reciente al más antiguo. */
@Schema(description = "Estado efectivo de la última credencial y eventos del número, del más reciente al más antiguo.")
public record HistorialCredencialResponse(
        @Schema(description = "Número de WhatsApp en formato E.164",
                example = "+5491111111111")
        String numeroWhatsapp,
        @Schema(description = "Tipo documental del cliente en H1",
                example = "DNI")
        String documentoTipo,
        @Schema(description = "Número documental del cliente en H1",
                example = "12345678")
        String documentoNumero,
        @Schema(description = "Estado efectivo de la última credencial; ACTIVA vencida se informa EXPIRADA",
                example = "ACTIVA")
        CredencialEstado estadoActual,
        @Schema(description = "Eventos del número del más reciente al más antiguo",
                example =
                        "[{\"accion\":\"EMISION\",\"resultado\":\"EXITOSO\",\"fecha\":\"2026-10-26T12:00:0"
                        + "0Z\",\"motivo\":\"EMISION\"}]")
        List<EventoCredencialResponse> eventos) {

    public HistorialCredencialResponse {
        eventos = List.copyOf(eventos);
    }
}
