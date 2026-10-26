package com.bcra.channel.ws.vinculo.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;

import com.bcra.channel.ws.vinculo.entity.MotivoBloqueo;

/**
 * Pedido de bloqueo del vínculo.
 * @param motivo causa del bloqueo ({@link MotivoBloqueo#SOLICITUD_CLIENTE} exige credencial válida)
 * @param confirmacion debe ser {@code true}; ausencia o {@code false} → 400 VALIDATION_ERROR
 */
public record BloquearVinculoRequest(
        @NotNull(message = "El motivo de bloqueo es obligatorio")
        MotivoBloqueo motivo,
        @NotNull(message = "La confirmación es obligatoria")
        @AssertTrue(message = "Debe confirmar explícitamente el bloqueo")
        Boolean confirmacion) {
}
