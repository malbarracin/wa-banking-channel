package com.bank.users.exception;

/** Campo inválido y motivo público, sin el valor rechazado. */
public record FieldError(String field, String message) {
}
