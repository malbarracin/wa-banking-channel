package com.wa.banking.channel.entity;

/**
 * Acciones auditables del ciclo de vida del vínculo de canal.
 *
 * @author licius-it
 * @since 2026-06-23
 * @contact marceloalejandro.albarracin@gmail.com
 */
public enum AuditAction {

    TERMS_ACCEPTED,
    VERIFICATION_ATTEMPT,
    LINKED,
    BLOCKED,
    UNLINKED,
    RELINKED,
    CREDENTIAL_REQUESTED,
    CREDENTIAL_REVOKED
}
