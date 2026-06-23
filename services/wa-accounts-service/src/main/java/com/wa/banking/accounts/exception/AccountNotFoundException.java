package com.wa.banking.accounts.exception;

/**
 * Excepción de dominio cuando una cuenta no existe o no pertenece al titular autenticado.
 *
 * @author licius-it
 * @since 2026-06-23
 * @contact marceloalejandro.albarracin@gmail.com
 */
public class AccountNotFoundException extends RuntimeException {

    public AccountNotFoundException(String accountId) {
        super("Account not found: " + accountId);
    }
}
