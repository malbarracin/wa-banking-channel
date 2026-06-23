package com.wa.banking.auth.exception;

/**
 * Excepción cuando una credencial no es válida para la operación solicitada.
 *
 * @author licius-it
 * @since 2026-06-23
 * @contact marceloalejandro.albarracin@gmail.com
 */
public class InvalidCredentialException extends RuntimeException {

    public InvalidCredentialException(String message) {
        super(message);
    }
}
