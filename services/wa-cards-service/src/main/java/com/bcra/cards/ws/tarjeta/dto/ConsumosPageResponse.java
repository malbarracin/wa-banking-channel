package com.bcra.cards.ws.tarjeta.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/** Página inmutable; cursor nulo cuando no quedan consumos. */
public record ConsumosPageResponse(
        @Schema(
            description = "Consumos ordenados por fecha e identificador descendentes") List<ConsumoResponse> consumos,
        @Schema(description = "Indica si hay otra página", example = "false") boolean tieneMas,
        @Schema(description = "Cursor opaco de la página siguiente; null al finalizar") String cursor) {

    public ConsumosPageResponse {
        consumos = List.copyOf(consumos);
    }
}
