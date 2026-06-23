package com.wa.banking.auth.exception;

/**
 * Excepción cuando el usuario bancario no es elegible para emitir credencial de sesión.
 *
 * @author licius-it
 * @since 2026-06-23
 * @contact marceloalejandro.albarracin@gmail.com
 */
public class UserNotEligibleException extends RuntimeException {

    public UserNotEligibleException(String bankUserId) {
        super("Bank user is not eligible for session credential: " + bankUserId);
    }
}
