package com.bcra.accounts.ws.exception;

/** Base de los errores de dominio de tipo recurso inexistente. */
public abstract class NotFoundException extends RuntimeException {

    protected NotFoundException(String message) {
        super(message);
    }
}
