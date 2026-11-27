package com.bcra.channel.ws.exception;

/**
 * Intentos de verificación agotados (HTTP 423 / {@link ErrorCode#VERIFICATION_LOCKED}).
 */
public class VerificationLockedException extends RuntimeException {

    public VerificationLockedException(String message) {
        super(message);
    }
}
