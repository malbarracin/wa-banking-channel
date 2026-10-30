package com.bcra.cards.ws.tarjeta.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/** Solicitud de bloqueo con identificador y confirmación explícita del titular. */
public record BloqueoTemporalRequest(
        @Schema(description = "Identificador de tarjeta; debe coincidir con el path",
                example = "11111111-1111-4111-8111-111111111111",
                requiredMode = Schema.RequiredMode.REQUIRED) @NotNull UUID tarjetaId,
        @Schema(description = "Confirmación exacta CONFIRMAR", example = "CONFIRMAR") String confirmar) {
}
