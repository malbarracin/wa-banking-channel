package com.bcra.channel.ws.exception;

/**
 * Acceso denegado a un endpoint interno (HTTP 403 / {@link ErrorCode#FORBIDDEN}).
 */
public class ForbiddenException extends RuntimeException {

    public ForbiddenException(String message) {
        super(message);
    }
}
