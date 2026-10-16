package com.bank.users.exception;

/** Base de errores de dominio por recurso inexistente. */
public abstract class NotFoundException extends RuntimeException {

    protected NotFoundException(String message) {
        super(message);
    }
}
