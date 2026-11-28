package com.bcra.channel.ws.binding.dto;

import java.util.UUID;

import com.bcra.channel.ws.binding.entity.BindingStatus;
import com.fasterxml.jackson.annotation.JsonInclude;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Respuesta de verificación de identidad.
 *
 * @param bindingId         vínculo afectado
 * @param status            estado tras el intento
 * @param attemptsRemaining intentos restantes en la ventana (0 si bloqueado o éxito)
 * @param verifiableResult  presente solo si el vínculo quedó {@link BindingStatus#ACTIVO}
 * @param message           texto al cliente (sin secretos ni ids internos)
 */
@Schema(description = "Resultado de un intento de verificación de identidad")
@JsonInclude(JsonInclude.Include.NON_NULL)
public record VerifyIdentityResponse(
        @Schema(description = "Id del vínculo", example = "b2c3d4e5-f6a7-8901-bcde-f12345678901")
        UUID bindingId,

        @Schema(description = "Estado del vínculo tras el intento", example = "ACTIVO")
        BindingStatus status,

        @Schema(description = "Intentos de verificación restantes en la ventana", example = "2")
        int attemptsRemaining,

        @Schema(description = "Resultado verificable; solo si status = ACTIVO")
        VerifiableResultResponse verifiableResult,

        @Schema(description = "Mensaje al cliente sin secretos ni ids internos")
        String message) {
}
