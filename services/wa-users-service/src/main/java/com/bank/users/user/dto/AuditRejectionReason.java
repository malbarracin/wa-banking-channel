package com.bank.users.user.dto;

/**
 * Motivo codificado de un rechazo auditado. Es un conjunto cerrado para que la auditoría
 * nunca contenga texto libre con datos personales (documento, email, nombres).
 */
public enum AuditRejectionReason {
    /** Ya existe un cliente con el mismo tipo y número de documento, en cualquier estado. */
    DUPLICATE_DOCUMENT
}
