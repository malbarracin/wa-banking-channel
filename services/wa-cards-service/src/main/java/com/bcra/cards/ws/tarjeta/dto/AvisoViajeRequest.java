package com.bcra.cards.ws.tarjeta.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.UUID;

/** Destino y fechas que el titular confirma al solicitar el registro. */
public record AvisoViajeRequest(
        @NotNull UUID tarjetaId,
        @NotBlank @Size(max = 255) String pais,
        @Size(max = 255) String region,
        @NotNull LocalDate fechaDesde,
        @NotNull LocalDate fechaHasta) {
}
