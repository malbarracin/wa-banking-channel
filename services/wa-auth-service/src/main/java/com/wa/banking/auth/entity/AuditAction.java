package com.wa.banking.auth.entity;

/**
 * Acción registrada en el historial de auditoría de credenciales.
 *
 * @author licius-it
 * @since 2026-06-23
 * @contact marceloalejandro.albarracin@gmail.com
 */
public enum AuditAction {

    ISSUED,
    RENEWED,
    REVOKED,
    VALIDATED,
    VALIDATION_FAILED,
    REPLACED
}
