package com.bcra.ws.users.api.error;

/**
 * Excepción base de scaffold para recursos inexistentes (404).
 * Las excepciones de dominio específicas se agregan en el step build.
 *
 * @since 2026-10-19
 */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }
}
