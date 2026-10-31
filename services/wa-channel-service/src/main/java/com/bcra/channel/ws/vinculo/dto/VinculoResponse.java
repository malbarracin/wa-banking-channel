package com.bcra.channel.ws.vinculo.dto;

import java.time.Instant;
import java.util.UUID;

import com.bcra.channel.ws.vinculo.entity.VinculoEstado;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Estado público del vínculo; {@code fechaActivacion} es nula hasta completar la verificación.
 *
 * @param id identificador del vínculo
 * @param numeroWhatsapp número E.164 del canal
 * @param estado estado actual del vínculo
 * @param fechaActivacion momento de activación; null si aún no está activo
 * @param preferencias preferencias actuales del canal
 */
@Schema(description = "Estado público del vínculo del canal")
public record VinculoResponse(
        @Schema(description = "Identificador del vínculo", example = "a1b2c3d4-e5f6-7890-abcd-ef1234567890")
        UUID id,
        @Schema(description = "Número E.164 del canal", example = "+5491112345678")
        String numeroWhatsapp,
        @Schema(description = "Estado actual: PENDIENTE_VERIFICACION, ACTIVO, BLOQUEADO o DESVINCULADO",
                example = "PENDIENTE_VERIFICACION")
        VinculoEstado estado,
        @Schema(description = "Instante UTC de activación; null mientras no esté ACTIVO",
                example = "2026-10-26T12:00:00Z")
        Instant fechaActivacion,
        PreferenciasResponse preferencias) {
}
