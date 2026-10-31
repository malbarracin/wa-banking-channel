package com.bcra.cards.ws.tarjeta.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/** Solicitud de desbloqueo con el resultado de la validación adicional. */
public record DesbloqueoRequest(
        @Schema(description = "Identificador de tarjeta; debe coincidir con el path",
                example = "11111111-1111-4111-8111-111111111111",
                requiredMode = Schema.RequiredMode.REQUIRED) @NotNull UUID tarjetaId,
        @Schema(description = "Resultado de la validación adicional exigida por el banco",
                example = "true") Boolean validacionAdicional) {
}
