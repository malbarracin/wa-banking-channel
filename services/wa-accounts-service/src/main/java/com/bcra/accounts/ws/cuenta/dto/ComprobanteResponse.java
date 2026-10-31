package com.bcra.accounts.ws.cuenta.dto;

import java.math.BigDecimal;
import java.time.Instant;

import io.swagger.v3.oas.annotations.media.Schema;

import com.bcra.accounts.ws.cuenta.entity.Moneda;

/** Comprobante resumido de una transferencia efectivamente ejecutada. */
public record ComprobanteResponse(
        @Schema(description = "Fecha ISO-8601 UTC",
                example = "2026-10-27T12:00:00Z")
        Instant fecha,
        @Schema(description = "Número de cuenta origen",
                example = "001")
        String origen,
        @Schema(description = "Número de cuenta destino",
                example = "002")
        String destino,
        @Schema(description = "Importe en la moneda indicada",
                example = "100.00")
        BigDecimal monto,
        @Schema(description = "Moneda de la operación: ARS o USD",
                example = "ARS")
        Moneda moneda,
        @Schema(description = "Referencia de la operación ejecutada",
                example = "ref-001")
        String referencia) {
}
