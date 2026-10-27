package com.bcra.accounts.ws.cuenta.dto;

import java.math.BigDecimal;

import io.swagger.v3.oas.annotations.media.Schema;

import com.bcra.accounts.ws.cuenta.entity.Moneda;

/** Saldos disponible para operar y contable registrado, expresados en la moneda de la cuenta. */
public record SaldoResponse(
        @Schema(description = "Número de cuenta del titular",
                example = "001")
        String numeroCuenta,
        @Schema(description = "Alias de la cuenta o destinatario",
                example = "ahorros")
        String alias,
        @Schema(description = "Saldo disponible para operar",
                example = "15000.00")
        BigDecimal saldoDisponible,
        @Schema(description = "Saldo contable registrado",
                example = "15000.00")
        BigDecimal saldoContable,
        @Schema(description = "Moneda de la operación: ARS o USD",
                example = "ARS")
        Moneda moneda) {
}
