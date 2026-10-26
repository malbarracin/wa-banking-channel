package com.bcra.credentials.ws.exception;

import io.swagger.v3.oas.annotations.media.Schema;

/** Códigos estables del contrato de errores. */
@Schema(description = "VALIDATION_ERROR: validación; BAD_REQUEST: solicitud ilegible o inválida; "
        + "UNAUTHORIZED: credencial rechazada; NOT_FOUND: recurso inexistente; CONFLICT: conflicto de datos; "
        + "INTERNAL_ERROR: error interno; FORBIDDEN: emisión no autorizada; SERVICE_UNAVAILABLE: H1 indisponible.")
public enum ErrorCode {
    VALIDATION_ERROR,
    BAD_REQUEST,
    UNAUTHORIZED,
    NOT_FOUND,
    CONFLICT,
    INTERNAL_ERROR,
    FORBIDDEN,
    SERVICE_UNAVAILABLE
}
