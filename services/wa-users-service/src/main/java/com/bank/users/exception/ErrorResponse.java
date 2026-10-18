package com.bank.users.exception;

import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonFormat;

/** Respuesta común de error con fecha UTC y detalles inmutables. */
public record ErrorResponse(ErrorCode code, String message, List<FieldError> details,
        @JsonFormat(shape = JsonFormat.Shape.STRING) Instant timestamp) {

    public ErrorResponse {
        details = List.copyOf(details);
    }
}
