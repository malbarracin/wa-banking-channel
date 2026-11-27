package com.bcra.channel.ws.exception;

/**
 * Base para recursos inexistentes (HTTP 404 / {@link ErrorCode#NOT_FOUND}).
 */
public abstract class NotFoundException extends RuntimeException {

    protected NotFoundException(String message) {
        super(message);
    }
}
