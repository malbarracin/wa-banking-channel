package com.bcra.channel.ws.vinculo.dto;

import java.util.List;

import com.bcra.channel.ws.vinculo.entity.VinculoEstado;

/** Historial interno para soporte; eventos del más reciente al más antiguo, documento opcional. */
public record HistorialVinculoResponse(String numeroWhatsapp, String documentoTipo, String documentoNumero,
        VinculoEstado estadoActual, List<EventoHistorialResponse> eventos) {

    public HistorialVinculoResponse {
        eventos = List.copyOf(eventos);
    }

    @Override
    public String toString() {
        return "HistorialVinculoResponse[estadoActual=" + estadoActual + ", eventos=" + eventos.size() + "]";
    }
}
