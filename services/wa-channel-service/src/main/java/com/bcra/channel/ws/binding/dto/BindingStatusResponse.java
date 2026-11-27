package com.bcra.channel.ws.binding.dto;

import java.util.UUID;

import com.bcra.channel.ws.binding.entity.BindingStatus;
import com.fasterxml.jackson.annotation.JsonInclude;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Estado actual del vínculo de un número WhatsApp.
 *
 * @param whatsappNumber número en E.164
 * @param status         estado del vínculo ({@link BindingStatus#SIN_VINCULO} si no hay registro)
 * @param clientId       cliente vinculado; ausente si status es {@code SIN_VINCULO}
 * @param bindingId      identificador del vínculo; ausente si status es {@code SIN_VINCULO}
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Estado del vínculo de un número WhatsApp")
public record BindingStatusResponse(
        @Schema(description = "Número WhatsApp en E.164", example = "+5491112345678")
        String whatsappNumber,
        @Schema(description = "Estado del vínculo", example = "ACTIVO")
        BindingStatus status,
        @Schema(description = "Identificador del cliente vinculado", example = "c1d2e3f4-a5b6-7890-cdef-1234567890ab")
        UUID clientId,
        @Schema(description = "Identificador del vínculo", example = "b2c3d4e5-f6a7-8901-bcde-f12345678901")
        UUID bindingId) {
}
