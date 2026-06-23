package com.wa.banking.auth.exception;

/**
 * Excepción cuando se intenta emitir credencial sin verificación de identidad completada.
 *
 * @author licius-it
 * @since 2026-06-23
 * @contact marceloalejandro.albarracin@gmail.com
 */
public class VerificationRequiredException extends RuntimeException {

    public VerificationRequiredException() {
        super("Identity verification is required before issuing a session credential");
    }
}
