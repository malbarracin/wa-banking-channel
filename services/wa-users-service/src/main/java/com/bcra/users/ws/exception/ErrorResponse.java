package com.bcra.users.ws.exception;

import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonFormat;

/** Contrato único de errores; timestamp se serializa en UTC y details nunca es null. */
public record ErrorResponse(ErrorCode code, String message, List<FieldError> details,
        @JsonFormat(shape = JsonFormat.Shape.STRING) Instant timestamp) {

    public ErrorResponse {
        details = List.copyOf(details);
    }
}
