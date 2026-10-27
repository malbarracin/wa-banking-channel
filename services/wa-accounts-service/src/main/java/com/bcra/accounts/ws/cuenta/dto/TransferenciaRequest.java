package com.bcra.accounts.ws.cuenta.dto;

import java.math.BigDecimal;
import java.util.UUID;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.bcra.accounts.ws.cuenta.entity.Moneda;

/** Solicitud con exactamente un destino: cuenta propia o identificador de tercero agendado. */
public record TransferenciaRequest(
        @NotBlank @Size(max = 34) String cuentaOrigen,
        @Size(max = 34) String cuentaDestino,
        UUID terceroId,
        @NotNull @DecimalMin("0.01") @Digits(integer = 17, fraction = 2) BigDecimal monto,
        @NotNull Moneda moneda,
        @Size(max = 255) String concepto) {

    /** Evita destinos ambiguos o ausentes. */
    @AssertTrue(message = "Debe indicar una cuenta destino o un tercero, exclusivamente")
    public boolean isDestinoValido() {
        return (cuentaDestino != null && !cuentaDestino.isBlank()) != (terceroId != null)
                && (cuentaDestino == null || !cuentaDestino.isBlank());
    }
}

