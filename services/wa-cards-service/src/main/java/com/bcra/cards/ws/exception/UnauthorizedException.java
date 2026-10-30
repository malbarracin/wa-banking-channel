package com.bcra.cards.ws.exception;

/** Credencial no válida. */
public class UnauthorizedException extends RuntimeException {

    public UnauthorizedException(String message) {
        super(message);
    }
}
