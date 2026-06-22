package com.wa.banking.users.exception;

/**
 * Excepción de dominio cuando no se encuentra un usuario bancario.
 *
 * @author licius-it
 * @since 2026-06-22
 * @contact marceloalejandro.albarracin@gmail.com
 */
public class UserNotFoundException extends RuntimeException {

    public UserNotFoundException(String message) {
        super(message);
    }
}
