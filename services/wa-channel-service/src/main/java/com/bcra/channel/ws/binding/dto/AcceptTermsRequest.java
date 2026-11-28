package com.bcra.channel.ws.binding.dto;

import jakarta.validation.constraints.NotNull;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Pedido de aceptación o rechazo de términos y condiciones del canal.
 *
 * @param accepted {@code true} si el cliente acepta; {@code false} rechaza
 */
@Schema(description = "Aceptación de términos y condiciones del canal")
public record AcceptTermsRequest(
        @NotNull
        @Schema(description = "true acepta; false rechaza", example = "true", requiredMode = Schema.RequiredMode.REQUIRED)
        Boolean accepted) {
}
