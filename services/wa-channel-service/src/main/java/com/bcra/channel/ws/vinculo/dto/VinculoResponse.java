package com.bcra.channel.ws.vinculo.dto;

import java.time.Instant;
import java.util.UUID;

import com.bcra.channel.ws.vinculo.entity.VinculoEstado;

/** Estado público del vínculo; fechaActivacion es nula hasta completar la verificación. */
public record VinculoResponse(UUID id, String numeroWhatsapp, VinculoEstado estado, Instant fechaActivacion) {
}
