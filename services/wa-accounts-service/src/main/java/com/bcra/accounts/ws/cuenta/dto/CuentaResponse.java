package com.bcra.accounts.ws.cuenta.dto;

import com.bcra.accounts.ws.cuenta.entity.Moneda;
import com.bcra.accounts.ws.cuenta.entity.TipoCuenta;

/** Cuenta del titular, sin exponer documentos ni saldos en el listado. */
public record CuentaResponse(String numeroCuenta, String alias, TipoCuenta tipo, Moneda moneda) {
}
