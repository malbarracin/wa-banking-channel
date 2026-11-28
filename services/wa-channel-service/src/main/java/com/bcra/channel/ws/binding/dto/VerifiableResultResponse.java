package com.bcra.channel.ws.binding.dto;

import java.time.Instant;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Resultado verificable que habilita la coordinación de la credencial.
 *
 * @param identityVerified identidad confirmada
 * @param bindingId        vínculo activado
 * @param clientId         cliente vinculado
 * @param verifiedAt       instante de la verificación exitosa
 */
@Schema(description = "Resultado verificable de identidad y vínculo")
public record VerifiableResultResponse(
        @Schema(description = "true si la identidad fue verificada", example = "true")
        boolean identityVerified,

        @Schema(description = "Id del vínculo", example = "b2c3d4e5-f6a7-8901-bcde-f12345678901")
        UUID bindingId,

        @Schema(description = "Id del cliente", example = "123e4567-e89b-12d3-a456-426614174000")
        UUID clientId,

        @Schema(description = "Momento de la verificación", example = "2026-11-27T15:30:00Z")
        Instant verifiedAt) {
}
