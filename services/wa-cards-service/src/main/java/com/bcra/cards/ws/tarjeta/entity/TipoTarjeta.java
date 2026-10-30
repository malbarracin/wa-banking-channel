package com.bcra.cards.ws.tarjeta.entity;

import io.swagger.v3.oas.annotations.media.Schema;

/** Valores persistidos de TipoTarjeta. */
@Schema(description = "DEBITO: débito; CREDITO: crédito")
public enum TipoTarjeta {
    DEBITO,
    CREDITO
}

