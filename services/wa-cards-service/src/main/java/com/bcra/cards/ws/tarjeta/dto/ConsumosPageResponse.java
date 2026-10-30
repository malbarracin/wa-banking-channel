package com.bcra.cards.ws.tarjeta.dto;

import java.util.List;

/** Página inmutable; cursor nulo cuando no quedan consumos. */
public record ConsumosPageResponse(List<ConsumoResponse> consumos, boolean tieneMas, String cursor) {

    public ConsumosPageResponse {
        consumos = List.copyOf(consumos);
    }
}
