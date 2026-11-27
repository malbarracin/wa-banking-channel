package com.bcra.channel.ws.binding.dto;

import java.util.UUID;

import com.bcra.channel.ws.binding.entity.BindingStatus;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Resultado de la aceptación de términos.
 *
 * @param bindingId identificador del vínculo creado o reactivado
 * @param status    siempre {@link BindingStatus#PENDIENTE_VERIFICACION}
 * @param message   texto al cliente sin secretos
 */
@Schema(description = "Respuesta tras aceptar términos y condiciones")
public record AcceptTermsResponse(
        @Schema(description = "Identificador del vínculo", example = "b2c3d4e5-f6a7-8901-bcde-f12345678901")
        UUID bindingId,
        @Schema(description = "Estado resultante", example = "PENDIENTE_VERIFICACION")
        BindingStatus status,
        @Schema(
                description = "Mensaje al cliente",
                example = "Términos aceptados. Podés continuar con la verificación de identidad.")
        String message) {
}
