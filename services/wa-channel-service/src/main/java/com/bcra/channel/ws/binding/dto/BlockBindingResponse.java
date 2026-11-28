package com.bcra.channel.ws.binding.dto;

import java.util.UUID;

import com.bcra.channel.ws.binding.entity.BindingStatus;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Resultado del bloqueo del vínculo.
 *
 * @param bindingId                     identificador del vínculo
 * @param status                        siempre {@link BindingStatus#BLOQUEADO}
 * @param message                       texto 8.10 al cliente sin secretos
 * @param credentialRevocationRequired  indica al orquestador que debe revocar la credencial en el mismo intercambio
 */
@Schema(description = "Respuesta tras bloquear el vínculo")
public record BlockBindingResponse(
        @Schema(description = "Identificador del vínculo", example = "b2c3d4e5-f6a7-8901-bcde-f12345678901")
        UUID bindingId,
        @Schema(description = "Estado resultante", example = "BLOQUEADO")
        BindingStatus status,
        @Schema(
                description = "Mensaje al cliente (sección 8.10)",
                example = "Tu WhatsApp quedó bloqueado para operaciones. Para reactivarlo, seguí los pasos de verificación cuando quieras.")
        String message,
        @Schema(description = "true: el orquestador debe revocar la credencial en H2", example = "true")
        boolean credentialRevocationRequired) {
}
