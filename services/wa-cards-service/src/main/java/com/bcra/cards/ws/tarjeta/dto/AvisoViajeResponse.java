package com.bcra.cards.ws.tarjeta.dto;

import java.time.LocalDate;
import java.util.UUID;

/** Confirmación del aviso sin datos sensibles de la tarjeta. */
public record AvisoViajeResponse(UUID tarjetaId, String pais, String region,
        LocalDate fechaDesde, LocalDate fechaHasta, String mensajeConfirmacion) {
}
