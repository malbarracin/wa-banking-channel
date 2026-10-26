package com.bcra.channel.ws.vinculo.dto;

import java.time.Instant;
import java.util.UUID;

import com.bcra.channel.ws.vinculo.entity.VinculoEstado;

/**
 * Estado público del vínculo; {@code fechaActivacion} es nula hasta completar la verificación.
 *
 * @param id identificador del vínculo
 * @param numeroWhatsapp número E.164 del canal
 * @param estado estado actual del vínculo
 * @param fechaActivacion momento de activación; null si aún no está activo
 * @param preferencias preferencias actuales del canal
 */
public record VinculoResponse(
        UUID id,
        String numeroWhatsapp,
        VinculoEstado estado,
        Instant fechaActivacion,
        PreferenciasResponse preferencias) {
}
