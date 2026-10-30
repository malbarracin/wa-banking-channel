package com.bcra.cards.ws.tarjeta.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/** Solicitud de bloqueo con identificador y confirmación explícita del titular. */
public record BloqueoTemporalRequest(@NotNull UUID tarjetaId, String confirmar) {
}
