package com.bcra.ws.users.exception;

/** Detalle de validación sin el valor recibido. */
public record FieldError(String field, String message) {
}
