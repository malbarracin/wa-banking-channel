package com.wa.banking.accounts.exception;

/**
 * Excepción de dominio cuando la credencial de sesión es inválida o ausente.
 *
 * @author licius-it
 * @since 2026-06-23
 * @contact marceloalejandro.albarracin@gmail.com
 */
public class UnauthorizedException extends RuntimeException {

    public UnauthorizedException(String message) {
        super(message);
    }
}
