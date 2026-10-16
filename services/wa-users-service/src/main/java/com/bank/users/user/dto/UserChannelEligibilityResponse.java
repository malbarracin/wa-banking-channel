package com.bank.users.user.dto;

import java.util.UUID;

import com.bank.users.user.entity.UserStatus;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Contrato mínimo para consumidores; la aptitud es verdadera exclusivamente para usuarios activos.
 */
@Schema(description = "Aptitud de vínculo de canal para servicios consumidores (H3)")
public record UserChannelEligibilityResponse(
        @Schema(description = "Identificador interno del usuario",
                example = "a1b2c3d4-e5f6-7890-abcd-ef1234567890")
        UUID userId,
        @Schema(description = "Estado operativo actual", example = "ACTIVO",
                allowableValues = {"ACTIVO", "SUSPENDIDO", "BAJA_LOGICA"})
        UserStatus status,
        @Schema(description = "True únicamente si el usuario está ACTIVO", example = "true")
        boolean aptoParaVinculoCanal) {
}
