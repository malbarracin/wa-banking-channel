package com.wa.banking.auth.exception;

/**
 * Excepción cuando no se encuentra una credencial de sesión.
 *
 * @author licius-it
 * @since 2026-06-23
 * @contact marceloalejandro.albarracin@gmail.com
 */
public class CredentialNotFoundException extends RuntimeException {

    public CredentialNotFoundException(String credentialId) {
        super("Session credential not found: " + credentialId);
    }
}
