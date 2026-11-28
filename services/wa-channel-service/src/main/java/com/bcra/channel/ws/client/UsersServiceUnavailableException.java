package com.bcra.channel.ws.client;

/**
 * El servicio de usuarios (H1) no respondió de forma usable (5xx, timeout o error de red).
 * El handler global la traduce a 500 {@code INTERNAL_ERROR} sin exponer el cuerpo de H1.
 */
public class UsersServiceUnavailableException extends RuntimeException {

    /**
     * @param message mensaje técnico para logs (sin cuerpo de H1 ni PII)
     */
    public UsersServiceUnavailableException(String message) {
        super(message);
    }

    /**
     * @param message mensaje técnico para logs (sin cuerpo de H1 ni PII)
     * @param cause causa raíz (timeout, red, etc.)
     */
    public UsersServiceUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
