package com.bcra.channel.ws.exception;

/** Base para solicitudes sin contexto de credencial válido (HTTP 401). */
public abstract class UnauthorizedException extends RuntimeException {

    protected UnauthorizedException(String message) {
        super(message);
    }
}
