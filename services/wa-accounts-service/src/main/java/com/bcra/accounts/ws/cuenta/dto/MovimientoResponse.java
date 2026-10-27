package com.bcra.accounts.ws.cuenta.dto;

import java.math.BigDecimal;
import java.time.Instant;

import io.swagger.v3.oas.annotations.media.Schema;

import com.bcra.accounts.ws.cuenta.entity.Moneda;
import com.bcra.accounts.ws.cuenta.entity.TipoMovimiento;

/** Movimiento contable; fecha en UTC y montos en la moneda indicada. */
public record MovimientoResponse(
        @Schema(description = "Fecha ISO-8601 UTC",
                example = "2026-10-27T12:00:00Z")
        Instant fecha,
        @Schema(description = "Sentido del movimiento: DEBITO (egreso) o CREDITO (ingreso)",
                example = "DEBITO")
        TipoMovimiento tipo,
        @Schema(description = "Importe en la moneda indicada",
                example = "100.00")
        BigDecimal monto,
        @Schema(description = "Moneda de la operación: ARS o USD",
                example = "ARS")
        Moneda moneda,
        @Schema(description = "Descripción del movimiento",
                example = "Transferencia")
        String descripcion,
        @Schema(description = "Referencia de la operación ejecutada",
                example = "ref-001")
        String referencia,
        @Schema(description = "Saldo contable luego del movimiento",
                example = "14900.00")
        BigDecimal saldoResultante) {
}
