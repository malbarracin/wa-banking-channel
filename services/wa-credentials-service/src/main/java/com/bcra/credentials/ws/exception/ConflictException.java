package com.bcra.credentials.ws.exception;

/** Base de los errores de dominio de tipo conflicto. */
public abstract class ConflictException extends RuntimeException {

    protected ConflictException(String message) {
        super(message);
    }
}
