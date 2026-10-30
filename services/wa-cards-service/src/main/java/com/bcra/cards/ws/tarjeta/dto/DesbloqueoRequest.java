package com.bcra.cards.ws.tarjeta.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/** Solicitud de desbloqueo con el resultado de la validación adicional. */
public record DesbloqueoRequest(@NotNull UUID tarjetaId, Boolean validacionAdicional) {
}
