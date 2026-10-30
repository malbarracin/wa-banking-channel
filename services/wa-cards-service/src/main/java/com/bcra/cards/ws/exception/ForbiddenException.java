package com.bcra.cards.ws.exception;

/** Acceso no permitido. */
public class ForbiddenException extends RuntimeException {

    public ForbiddenException(String message) {
        super(message);
    }
}
