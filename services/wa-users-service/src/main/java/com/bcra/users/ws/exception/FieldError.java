package com.bcra.users.ws.exception;

/** Describes a field validation error. */
public record FieldError(String field, String message) {
}
