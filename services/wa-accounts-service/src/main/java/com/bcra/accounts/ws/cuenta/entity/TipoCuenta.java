package com.bcra.accounts.ws.cuenta.entity;

import io.swagger.v3.oas.annotations.media.Schema;

/** Tipos de cuenta: caja de ahorro y cuenta corriente. */
@Schema(description = "CA: caja de ahorro; CC: cuenta corriente")
public enum TipoCuenta {
    CA, CC
}

