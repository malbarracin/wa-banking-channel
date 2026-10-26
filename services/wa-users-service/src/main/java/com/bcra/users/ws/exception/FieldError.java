package com.bcra.users.ws.exception;

/** Detalle de validación sin incluir el valor rechazado. */
public record FieldError(String field, String message) {
}
