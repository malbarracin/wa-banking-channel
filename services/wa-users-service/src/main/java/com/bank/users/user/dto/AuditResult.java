package com.bank.users.user.dto;

/**
 * Resultado registrado en un evento de auditoría.
 */
public enum AuditResult {
    /** La operación auditada se completó. */
    OK,
    /** La operación auditada fue rechazada. */
    REJECTED
}
