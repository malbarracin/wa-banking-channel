package com.bcra.accounts.ws.cuenta.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import com.bcra.accounts.ws.cuenta.entity.Moneda;
import com.bcra.accounts.ws.cuenta.entity.TipoCuenta;

/** Cuenta del titular, sin exponer documentos ni saldos en el listado. */
public record CuentaResponse(
        @Schema(description = "Número de cuenta del titular",
                example = "001")
        String numeroCuenta,
        @Schema(description = "Alias de la cuenta o destinatario",
                example = "ahorros")
        String alias,
        @Schema(description = "Tipo de cuenta: CA (caja de ahorro) o CC (cuenta corriente)",
                example = "CA")
        TipoCuenta tipo,
        @Schema(description = "Moneda de la operación: ARS o USD",
                example = "ARS")
        Moneda moneda) {
}
