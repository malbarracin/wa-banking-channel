package com.wa.banking.auth.exception;

/**
 * Excepción cuando se intenta operar sobre una credencial ya revocada.
 *
 * @author licius-it
 * @since 2026-06-23
 * @contact marceloalejandro.albarracin@gmail.com
 */
public class CredentialAlreadyRevokedException extends RuntimeException {

    public CredentialAlreadyRevokedException(String credentialId) {
        super("Session credential already revoked: " + credentialId);
    }
}
