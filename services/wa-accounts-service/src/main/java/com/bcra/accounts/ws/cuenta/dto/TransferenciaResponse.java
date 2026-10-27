package com.bcra.accounts.ws.cuenta.dto;

import java.time.Instant;
import java.util.UUID;

import com.bcra.accounts.ws.cuenta.entity.EstadoTransferencia;

/** CONFIRMADA requiere otra confirmación; solo EJECUTADA incluye comprobante y referencia. */
public record TransferenciaResponse(UUID transferenciaId, EstadoTransferencia estado, Instant fechaEjecucion,
        String referencia, ComprobanteResponse comprobante) {
}
