package com.wa.banking.accounts.api.v1.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

/**
 * DTO de respuesta con saldo disponible y contable de una cuenta.
 *
 * @author licius-it
 * @since 2026-06-23
 * @contact marceloalejandro.albarracin@gmail.com
 */
@Schema(description = "Saldo disponible y contable de una cuenta")
public record AccountBalanceResponse(

        @Schema(description = "Identificador de la cuenta", example = "acc-001")
        String accountId,

        @Schema(description = "Alias de la cuenta", example = "Cuenta Sueldo")
        String alias,

        @Schema(description = "Moneda ISO 4217", example = "ARS")
        String currency,

        @Schema(description = "Saldo disponible para operar", example = "125000.50")
        BigDecimal availableBalance,

        @Schema(description = "Saldo contable", example = "125000.50")
        BigDecimal ledgerBalance
) {
}
