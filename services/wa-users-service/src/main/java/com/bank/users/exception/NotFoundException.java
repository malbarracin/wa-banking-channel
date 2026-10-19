package com.bank.users.exception;

/** Base de errores de recursos inexistentes; el mensaje debe ser seguro para el cliente. */
public abstract class NotFoundException extends RuntimeException {

    protected NotFoundException(String message) {
        super(message);
    }
}
