package com.bcra.channel.ws.exception;

import java.time.Instant;
import java.util.List;

/**
 * Cuerpo único de error de la API.
 *
 * @param code      código estable para el cliente
 * @param message   texto legible, sin internals
 * @param details   detalles por campo; nunca {@code null}
 * @param timestamp instante UTC del error
 */
public record ErrorResponse(ErrorCode code, String message, List<FieldError> details, Instant timestamp) {
}
