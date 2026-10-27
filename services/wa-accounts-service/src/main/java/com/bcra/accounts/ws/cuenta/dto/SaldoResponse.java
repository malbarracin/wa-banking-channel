package com.bcra.accounts.ws.cuenta.dto;

import java.math.BigDecimal;

import com.bcra.accounts.ws.cuenta.entity.Moneda;

/** Saldos disponible para operar y contable registrado, expresados en la moneda de la cuenta. */
public record SaldoResponse(String numeroCuenta, String alias, BigDecimal saldoDisponible,
        BigDecimal saldoContable, Moneda moneda) {
}
