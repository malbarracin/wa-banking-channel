package com.bank.users.exception;

/** Base de errores de dominio por duplicados o transiciones incompatibles. */
public abstract class ConflictException extends RuntimeException {

    protected ConflictException(String message) {
        super(message);
    }
}
