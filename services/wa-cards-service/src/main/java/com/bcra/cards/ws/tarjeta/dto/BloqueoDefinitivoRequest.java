package com.bcra.cards.ws.tarjeta.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/** Solicitud de bloqueo irreversible con dos confirmaciones explícitas. */
public record BloqueoDefinitivoRequest(
        @Schema(description = "Identificador de tarjeta; debe coincidir con el path",
                example = "11111111-1111-4111-8111-111111111111",
                requiredMode = Schema.RequiredMode.REQUIRED) @NotNull UUID tarjetaId,
        @Schema(description = "Motivo: ROBO o EXTRAVIO", example = "ROBO") String motivo,
        @Schema(description = "Primera confirmación exacta CONFIRMAR", example = "CONFIRMAR") String confirmarPrimero,
        @Schema(description = "Segunda confirmación exacta CONFIRMAR", example = "CONFIRMAR")
        String confirmarSegundo) {
}
