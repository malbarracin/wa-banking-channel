package com.bcra.channel.ws.vinculo.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;

import com.bcra.channel.ws.vinculo.entity.MotivoBloqueo;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Pedido de bloqueo del vínculo.
 * @param motivo causa del bloqueo ({@link MotivoBloqueo#SOLICITUD_CLIENTE} exige credencial válida)
 * @param confirmacion debe ser {@code true}; ausencia o {@code false} → 400 VALIDATION_ERROR
 */
@Schema(description = "Bloqueo del vínculo: motivo y confirmación explícita")
public record BloquearVinculoRequest(
        @NotNull(message = "El motivo de bloqueo es obligatorio")
        @Schema(description = "Motivo: SOLICITUD_CLIENTE (requiere credencial válida), POLITICA_BANCO "
                + "o INTENTOS_AGOTADOS", example = "SOLICITUD_CLIENTE",
                requiredMode = Schema.RequiredMode.REQUIRED)
        MotivoBloqueo motivo,
        @NotNull(message = "La confirmación es obligatoria")
        @AssertTrue(message = "Debe confirmar explícitamente el bloqueo")
        @Schema(description = "Debe ser true; false o ausente → 400 VALIDATION_ERROR", example = "true",
                requiredMode = Schema.RequiredMode.REQUIRED)
        Boolean confirmacion) {
}
