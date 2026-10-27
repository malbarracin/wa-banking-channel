package com.bcra.accounts.ws.cuenta.dto;

import java.math.BigDecimal;
import java.time.Instant;

import com.bcra.accounts.ws.cuenta.entity.Moneda;

/** Comprobante resumido de una transferencia efectivamente ejecutada. */
public record ComprobanteResponse(Instant fecha, String origen, String destino, BigDecimal monto,
        Moneda moneda, String referencia) {
}
