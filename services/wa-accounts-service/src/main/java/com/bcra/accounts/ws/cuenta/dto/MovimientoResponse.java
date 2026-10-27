package com.bcra.accounts.ws.cuenta.dto;

import java.math.BigDecimal;
import java.time.Instant;

import com.bcra.accounts.ws.cuenta.entity.Moneda;
import com.bcra.accounts.ws.cuenta.entity.TipoMovimiento;

/** Movimiento contable; fecha en UTC y montos en la moneda indicada. */
public record MovimientoResponse(Instant fecha, TipoMovimiento tipo, BigDecimal monto, Moneda moneda,
        String descripcion, String referencia, BigDecimal saldoResultante) {
}
