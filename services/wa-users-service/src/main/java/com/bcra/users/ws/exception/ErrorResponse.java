package com.bcra.users.ws.exception;

import java.time.Instant;
import java.util.List;

public record ErrorResponse(ErrorCode code, String message, List<FieldError> details, Instant timestamp) {
    public ErrorResponse {
        details = List.copyOf(details);
    }
}
