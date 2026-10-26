package com.bcra.channel.ws.vinculo.dto;

import java.util.List;

import com.bcra.channel.ws.vinculo.entity.VinculoEstado;

import io.swagger.v3.oas.annotations.media.Schema;

/** Historial interno para soporte; eventos del más reciente al más antiguo, documento opcional. */
@Schema(description = "Historial resumido para soporte: estado actual y eventos del más reciente al más antiguo")
public record HistorialVinculoResponse(
        @Schema(description = "Número consultado", example = "+5491112345678") String numeroWhatsapp,
        @Schema(description = "Tipo de documento del titular; puede ser null", example = "DNI")
        String documentoTipo,
        @Schema(description = "Número de documento del titular; puede ser null", example = "30111222")
        String documentoNumero,
        @Schema(description = "Estado actual del vínculo", example = "ACTIVO") VinculoEstado estadoActual,
        List<EventoHistorialResponse> eventos) {

    public HistorialVinculoResponse {
        eventos = List.copyOf(eventos);
    }

    @Override
    public String toString() {
        return "HistorialVinculoResponse[estadoActual=" + estadoActual + ", eventos=" + eventos.size() + "]";
    }
}
