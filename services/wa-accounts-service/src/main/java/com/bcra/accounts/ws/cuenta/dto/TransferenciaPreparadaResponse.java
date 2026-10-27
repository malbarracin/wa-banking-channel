package com.bcra.accounts.ws.cuenta.dto;

import java.time.Instant;
import java.util.UUID;

import com.bcra.accounts.ws.cuenta.entity.EstadoTransferencia;

/** Preparación sin débito. expiraEn es el instante UTC límite para confirmar. */
public record TransferenciaPreparadaResponse(UUID transferenciaId, EstadoTransferencia estado, String resumen,
        boolean requiereDobleConfirmacion, Instant expiraEn) {
}

