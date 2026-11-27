package com.bcra.channel.ws.exception;

/**
 * Base para conflictos de estado o duplicados (HTTP 409 / {@link ErrorCode#CONFLICT}).
 */
public abstract class ConflictException extends RuntimeException {

    protected ConflictException(String message) {
        super(message);
    }
}
