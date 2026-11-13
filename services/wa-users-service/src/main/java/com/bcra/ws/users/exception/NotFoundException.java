package com.bcra.ws.users.exception;

/** Base para recursos inexistentes. */
public abstract class NotFoundException extends RuntimeException {

    protected NotFoundException(String message) {
        super(message);
    }
}
