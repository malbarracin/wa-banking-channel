package com.bank.users.user.exception;

import lombok.Getter;

/** Identifica una propiedad ajena al contrato de actualización sin copiar su valor. */
@Getter
public class NonUpdatableFieldException extends RuntimeException {

    private final String field;

    public NonUpdatableFieldException(String field) {
        super("Campo no actualizable");
        this.field = field;
    }
}
