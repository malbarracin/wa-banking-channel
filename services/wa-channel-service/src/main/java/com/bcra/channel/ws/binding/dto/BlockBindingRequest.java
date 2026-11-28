package com.bcra.channel.ws.binding.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Pedido de bloqueo del vínculo.
 *
 * @param actor     quién solicita el bloqueo
 * @param reason    motivo sin datos de verificación
 * @param confirmed confirmación explícita obligatoria si {@code actor} es {@link BindingLifecycleActor#CLIENTE}
 */
@Schema(description = "Pedido de bloqueo del vínculo WhatsApp")
public record BlockBindingRequest(
        @NotNull
        @Schema(
                description = "Actor: CLIENTE o BANCO",
                example = "CLIENTE",
                requiredMode = Schema.RequiredMode.REQUIRED)
        BindingLifecycleActor actor,
        @NotBlank
        @Size(max = 512)
        @Schema(
                description = "Motivo del bloqueo (sin datos de verificación)",
                example = "Solicitud del cliente",
                requiredMode = Schema.RequiredMode.REQUIRED)
        String reason,
        @Schema(
                description = "Confirmación explícita; obligatoria en true cuando actor=CLIENTE",
                example = "true")
        Boolean confirmed) {
}
