package com.bcra.ws.users.exception;

import java.time.Instant;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

/** Contrato único de errores de la API. */
@Schema(description = "Error de la API sin datos internos")
public record ErrorResponse(ErrorCode code, String message, List<FieldError> details, Instant timestamp) {

    public ErrorResponse {
        details = details == null ? List.of() : List.copyOf(details);
        timestamp = timestamp == null ? Instant.now() : timestamp;
    }

    public static ErrorResponse of(ErrorCode code, String message) {
        return of(code, message, List.of());
    }

    public static ErrorResponse of(ErrorCode code, String message, List<FieldError> details) {
        return new ErrorResponse(code, message, details, Instant.now());
    }
}
