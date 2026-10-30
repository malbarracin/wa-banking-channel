package com.bcra.cards.ws.tarjeta.dto;

import com.bcra.cards.ws.tarjeta.entity.EstadoTarjeta;
import java.util.UUID;

/** Resultado del bloqueo y orientación para revertirlo. */
public record BloqueoTemporalResponse(UUID tarjetaId, EstadoTarjeta estado, String mensaje) {
}
