package com.bcra.channel.ws.exception;

/** Base para verificaciones temporalmente bloqueadas (HTTP 423). */
public abstract class LockedException extends RuntimeException {

    protected LockedException(String message) {
        super(message);
    }
}
