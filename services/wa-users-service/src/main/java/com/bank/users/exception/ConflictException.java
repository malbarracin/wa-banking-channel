package com.bank.users.exception;

/** Base de conflictos de dominio; el mensaje debe ser seguro para el cliente. */
public abstract class ConflictException extends RuntimeException {

    protected ConflictException(String message) {
        super(message);
    }
}
