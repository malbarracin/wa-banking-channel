package com.wa.banking.auth.entity;

/**
 * Motivo de revocación de una credencial de sesión.
 *
 * @author licius-it
 * @since 2026-06-23
 * @contact marceloalejandro.albarracin@gmail.com
 */
public enum RevokeReason {

    BLOCK,
    UNLINK,
    FRAUD,
    FAILED_ATTEMPTS,
    POLICY,
    REPLACED
}
