package com.bcra.cards.ws.tarjeta.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/** Solicitud de bloqueo irreversible con dos confirmaciones explícitas. */
public record BloqueoDefinitivoRequest(@NotNull UUID tarjetaId, String motivo,
        String confirmarPrimero, String confirmarSegundo) {
}
