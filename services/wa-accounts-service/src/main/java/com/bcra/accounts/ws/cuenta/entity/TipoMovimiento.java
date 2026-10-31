package com.bcra.accounts.ws.cuenta.entity;

import io.swagger.v3.oas.annotations.media.Schema;

/** Sentido del movimiento sobre el saldo de la cuenta. */
@Schema(description = "DEBITO: egreso de fondos; CREDITO: ingreso de fondos")
public enum TipoMovimiento {
    DEBITO, CREDITO
}

