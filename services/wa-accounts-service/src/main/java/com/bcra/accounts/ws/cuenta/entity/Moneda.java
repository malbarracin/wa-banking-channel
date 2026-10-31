package com.bcra.accounts.ws.cuenta.entity;

import io.swagger.v3.oas.annotations.media.Schema;

/** Monedas admitidas por el servicio de cuentas. */
@Schema(description = "ARS: peso argentino; USD: dólar estadounidense")
public enum Moneda {
    ARS, USD
}

