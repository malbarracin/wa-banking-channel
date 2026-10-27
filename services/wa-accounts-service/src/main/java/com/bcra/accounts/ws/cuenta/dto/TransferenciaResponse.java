package com.bcra.accounts.ws.cuenta.dto;

import java.time.Instant;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

import com.bcra.accounts.ws.cuenta.entity.EstadoTransferencia;

/** CONFIRMADA requiere otra confirmación; solo EJECUTADA incluye comprobante y referencia. */
public record TransferenciaResponse(
        @Schema(description = "Identificador devuelto por la preparación",
                example = "22222222-2222-4222-8222-222222222222")
        UUID transferenciaId,
        @Schema(description = "PREPARADA espera confirmación; CONFIRMADA espera la segunda; EJECUTADA tiene"
                    + " comprobante",
                example = "EJECUTADA")
        EstadoTransferencia estado,
        @Schema(description = "Fecha ISO-8601 UTC; nula mientras resta confirmación",
                example = "2026-10-27T12:01:00Z")
        Instant fechaEjecucion,
        @Schema(description = "Referencia de la operación ejecutada",
                example = "ref-001")
        String referencia,
        @Schema(description = "Comprobante; nulo hasta EJECUTADA",
                example = "{}")
        ComprobanteResponse comprobante) {
}
