package com.wa.banking.accounts.api.v1.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

/**
 * DTO de respuesta con datos resumidos de una cuenta del titular.
 *
 * @author licius-it
 * @since 2026-06-23
 * @contact marceloalejandro.albarracin@gmail.com
 */
@Schema(description = "Cuenta bancaria del titular autenticado")
public record AccountResponse(

        @Schema(description = "Identificador único de la cuenta", example = "acc-001")
        String id,

        @Schema(description = "Alias visible para el cliente", example = "Cuenta Sueldo")
        String alias,

        @Schema(description = "Tipo de producto", example = "CHECKING")
        String type,

        @Schema(description = "Moneda ISO 4217", example = "ARS")
        String currency,

        @Schema(description = "Saldo disponible para operar", example = "125000.50")
        BigDecimal availableBalance,

        @Schema(description = "Saldo contable", example = "125000.50")
        BigDecimal ledgerBalance,

        @Schema(description = "Estado de la cuenta", example = "ACTIVE")
        String status
) {
}
