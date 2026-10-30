package com.bcra.cards.ws.tarjeta.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.UUID;

/** Destino y fechas que el titular confirma al solicitar el registro. */
public record AvisoViajeRequest(
        @Schema(description = "Identificador de tarjeta; debe coincidir con el path",
                example = "11111111-1111-4111-8111-111111111111",
                requiredMode = Schema.RequiredMode.REQUIRED) @NotNull UUID tarjetaId,

        @Schema(description = "País de destino",
                example = "España", requiredMode = Schema.RequiredMode.REQUIRED,
                maxLength = 255) @NotBlank @Size(max = 255) String pais,

        @Schema(description = "Región de destino opcional",
                example = "Madrid", maxLength = 255) @Size(max = 255) String region,

        @Schema(description = "Inicio del viaje, fecha ISO-8601",
                example = "2026-12-01", requiredMode = Schema.RequiredMode.REQUIRED) @NotNull LocalDate fechaDesde,

        @Schema(description = "Fin del viaje, fecha ISO-8601 igual o posterior al inicio",
                example = "2026-12-15", requiredMode = Schema.RequiredMode.REQUIRED) @NotNull LocalDate fechaHasta) {
}
