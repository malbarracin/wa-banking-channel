package com.ingenia.wa.users.api.error;

/**
 * Excepción base de scaffold para recursos inexistentes (404).
 * Las excepciones de dominio específicas se agregan en el step build.
 *
 * @author Ingenia
 * @since 2026-10-19
 * Contacto: marcelo.albarracin@ingenia.la
 */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }
}
