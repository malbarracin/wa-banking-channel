package com.bank.users.exception;

/** Detalle de validación sin el valor rechazado. */
public record FieldError(String field, String message) {
}
