package com.bcra.accounts.ws.cuenta.dto;

import java.util.List;

/** Página de movimientos; cursor es nulo cuando no quedan registros. */
public record MovimientosPageResponse(List<MovimientoResponse> movimientos, boolean tieneMas, String cursor) {

    public MovimientosPageResponse {
        movimientos = List.copyOf(movimientos);
    }
}
