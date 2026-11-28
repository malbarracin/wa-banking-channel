package com.bcra.channel.ws.binding.dto;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Pedido de verificación de identidad. {@code verificationData} no se persiste ni se audita.
 *
 * @param clientId          id interno del cliente en el servicio de usuarios (lo provee el orquestador)
 * @param verificationData  dato de verificación según la política del banco
 */
@Schema(description = "Verificación de identidad del titular del vínculo")
public record VerifyIdentityRequest(
        @NotNull
        @Schema(
                description = "Id interno del cliente en wa-users-service",
                example = "123e4567-e89b-12d3-a456-426614174000",
                requiredMode = Schema.RequiredMode.REQUIRED)
        UUID clientId,

        @NotBlank
        @Schema(
                description = "Dato de verificación (no se almacena ni registra)",
                example = "+5491112345678",
                requiredMode = Schema.RequiredMode.REQUIRED)
        String verificationData) {
}
