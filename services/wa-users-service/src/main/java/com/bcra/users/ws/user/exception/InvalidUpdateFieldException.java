package com.bcra.users.ws.user.exception;

/** Campo que no está autorizado para actualización. */
public class InvalidUpdateFieldException extends RuntimeException {
    private final String field;

    public InvalidUpdateFieldException(String field) {
        this.field = field;
    }

    public String getField() {
        return field;
    }
}
