package com.bcra.cards.ws.tarjeta.entity;

import io.swagger.v3.oas.annotations.media.Schema;

/** Valores persistidos de EstadoTarjeta. */
@Schema(description = "ACTIVA: operativa; BLOQUEO_TEMPORAL: reversible; BLOQUEO_DEFINITIVO: irreversible por chat")
public enum EstadoTarjeta {
    ACTIVA,
    BLOQUEO_TEMPORAL,
    BLOQUEO_DEFINITIVO
}

