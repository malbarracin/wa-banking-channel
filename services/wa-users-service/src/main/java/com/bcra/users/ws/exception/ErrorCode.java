package com.bcra.users.ws.exception;

import io.swagger.v3.oas.annotations.media.Schema;

/** Códigos estables del contrato de error. */
@Schema(description = "Código de error de la API",
        allowableValues = {"VALIDATION_ERROR", "BAD_REQUEST", "NOT_FOUND", "CONFLICT", "INTERNAL_ERROR"})
public enum ErrorCode {
    VALIDATION_ERROR,
    BAD_REQUEST,
    NOT_FOUND,
    CONFLICT,
    INTERNAL_ERROR
}
