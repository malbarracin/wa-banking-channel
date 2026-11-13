package com.bcra.ws.users.exception;

import lombok.Getter;

/** Intento de modificar un campo fuera del contrato de actualización. */
@Getter
public class NonUpdatableFieldException extends IllegalArgumentException {

    private final String field;

    public NonUpdatableFieldException(String field) {
        super("Field is not updatable in this service");
        this.field = field;
    }
}
