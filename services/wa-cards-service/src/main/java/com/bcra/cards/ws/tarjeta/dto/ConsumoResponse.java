package com.bcra.cards.ws.tarjeta.dto;

import java.math.BigDecimal;
import java.time.Instant;

/** Datos públicos de un consumo, sin información sensible de la tarjeta. */
public record ConsumoResponse(Instant fecha, String comercio, BigDecimal monto, String moneda,
        String descripcion, String referencia) {
}
