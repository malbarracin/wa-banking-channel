package com.bank.users.user.exception;

import lombok.Getter;

/** Valor de patch inválido o contacto mínimo ausente después de aplicar el cambio. */
@Getter
public class InvalidUserUpdateException extends RuntimeException {

    private final String field;

    public InvalidUserUpdateException(String field, String message) {
        super(message);
        this.field = field;
    }
}
