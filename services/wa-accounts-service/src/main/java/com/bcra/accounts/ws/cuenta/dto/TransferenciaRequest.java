package com.bcra.accounts.ws.cuenta.dto;

import java.math.BigDecimal;
import java.util.UUID;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import io.swagger.v3.oas.annotations.media.Schema;

import com.bcra.accounts.ws.cuenta.entity.Moneda;

/** Solicitud con exactamente un destino: cuenta propia o identificador de tercero agendado. */
public record TransferenciaRequest(
        @Schema(description = "Cuenta propia de origen; máximo 34 caracteres",
                example = "001", requiredMode = Schema.RequiredMode.REQUIRED, maxLength = 34)
        @NotBlank @Size(max = 34) String cuentaOrigen,
        @Schema(description = "Cuenta propia destino; excluyente con terceroId; máximo 34 caracteres",
                example = "002", maxLength = 34)
        @Size(max = 34) String cuentaDestino,
        @Schema(description = "Destinatario activo agendado; excluyente con cuentaDestino",
                example = "11111111-1111-4111-8111-111111111111")
        UUID terceroId,
        @Schema(description = "Monto positivo; hasta 17 enteros y 2 decimales",
                example = "100.00", requiredMode = Schema.RequiredMode.REQUIRED, minimum = "0.01")
        @NotNull @DecimalMin("0.01") @Digits(integer = 17, fraction = 2) BigDecimal monto,
        @Schema(description = "Moneda de la operación: ARS o USD",
                example = "ARS", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull Moneda moneda,
        @Schema(description = "Concepto opcional; máximo 255 caracteres",
                example = "Transferencia", maxLength = 255)
        @Size(max = 255) String concepto) {

    /** Evita destinos ambiguos o ausentes. */
    @Schema(hidden = true)
    @AssertTrue(message = "Debe indicar una cuenta destino o un tercero, exclusivamente")
    public boolean isDestinoValido() {
        return (cuentaDestino != null && !cuentaDestino.isBlank()) != (terceroId != null)
                && (cuentaDestino == null || !cuentaDestino.isBlank());
    }
}

