package com.bcra.accounts.ws.cuenta.dto;

import java.time.Instant;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

import com.bcra.accounts.ws.cuenta.entity.EstadoTransferencia;

/** Preparación sin débito. expiraEn es el instante UTC límite para confirmar. */
public record TransferenciaPreparadaResponse(
        @Schema(description = "Identificador devuelto por la preparación",
                example = "22222222-2222-4222-8222-222222222222")
        UUID transferenciaId,
        @Schema(description = "PREPARADA espera confirmación; CONFIRMADA espera la segunda; EJECUTADA tiene"
                    + " comprobante",
                example = "PREPARADA")
        EstadoTransferencia estado,
        @Schema(description = "Resumen de la operación a confirmar",
                example = "Transferir 100.00 ARS desde ahorros a gastos")
        String resumen,
        @Schema(description = "Exige dos confirmaciones consecutivas antes del débito",
                example = "false")
        boolean requiereDobleConfirmacion,
        @Schema(description = "Vencimiento ISO-8601 UTC de la preparación",
                example = "2026-10-27T12:05:00Z")
        Instant expiraEn) {
}

