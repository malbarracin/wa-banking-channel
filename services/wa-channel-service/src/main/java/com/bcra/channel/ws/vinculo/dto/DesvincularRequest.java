package com.bcra.channel.ws.vinculo.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;

/**
 * Pedido de desvinculación del canal a pedido del cliente.
 * @param confirmacion debe ser {@code true}; ausencia o {@code false} → 400 VALIDATION_ERROR
 */
public record DesvincularRequest(
        @NotNull(message = "La confirmación es obligatoria")
        @AssertTrue(message = "Debe confirmar explícitamente la desvinculación")
        Boolean confirmacion) {
}
