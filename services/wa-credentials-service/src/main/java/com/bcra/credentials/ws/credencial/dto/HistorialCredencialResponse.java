package com.bcra.credentials.ws.credencial.dto;

import java.util.List;

import com.bcra.credentials.ws.credencial.entity.CredencialEstado;

/** Estado efectivo de la última credencial y eventos del número, del más reciente al más antiguo. */
public record HistorialCredencialResponse(String numeroWhatsapp, String documentoTipo, String documentoNumero,
        CredencialEstado estadoActual, List<EventoCredencialResponse> eventos) {

    public HistorialCredencialResponse {
        eventos = List.copyOf(eventos);
    }
}
