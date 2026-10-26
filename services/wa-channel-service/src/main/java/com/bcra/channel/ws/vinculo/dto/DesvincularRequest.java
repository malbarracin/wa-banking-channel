package com.bcra.channel.ws.vinculo.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Pedido de desvinculación del canal a pedido del cliente.
 * @param confirmacion debe ser {@code true}; ausencia o {@code false} → 400 VALIDATION_ERROR
 */
@Schema(description = "Desvinculación del canal con confirmación explícita del cliente")
public record DesvincularRequest(
        @NotNull(message = "La confirmación es obligatoria")
        @AssertTrue(message = "Debe confirmar explícitamente la desvinculación")
        @Schema(description = "Debe ser true; false o ausente → 400 VALIDATION_ERROR", example = "true",
                requiredMode = Schema.RequiredMode.REQUIRED)
        Boolean confirmacion) {
}
