package com.bcra.accounts.ws.cuenta.dto;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

/** Página de movimientos; cursor es nulo cuando no quedan registros. */
public record MovimientosPageResponse(
        @Schema(description = "Movimientos ordenados por fecha e id descendentes",
                example = "[]")
        List<MovimientoResponse> movimientos,
        @Schema(description = "Indica si quedan movimientos por consultar",
                example = "false")
        boolean tieneMas,
        @Schema(description = "Cursor opaco; nulo al finalizar. Reutilizar sin modificar y conservar filtros",
                example = "null")
        String cursor) {

    public MovimientosPageResponse {
        movimientos = List.copyOf(movimientos);
    }
}
