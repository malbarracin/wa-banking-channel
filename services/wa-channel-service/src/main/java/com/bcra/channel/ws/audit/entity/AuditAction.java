package com.bcra.channel.ws.audit.entity;

/**
 * Acciones del ciclo de vida del vínculo registradas en auditoría.
 */
public enum AuditAction {

    /** Consulta del estado del vínculo. */
    STATUS_QUERIED,

    /** Aceptación de términos y condiciones. */
    TERMS_ACCEPTED,

    /** Rechazo de términos y condiciones. */
    TERMS_REJECTED,

    /** Búsqueda del cliente en el registro del banco. */
    CLIENT_LOOKUP,

    /** Verificación de identidad exitosa. */
    IDENTITY_VERIFIED,

    /** Verificación de identidad fallida. */
    IDENTITY_FAILED,

    /** Tope de intentos de verificación agotado. */
    VERIFICATION_LOCKED,

    /** Control anti-duplicados / anti-fraude. */
    ANTIFRAUD_CHECK,

    /** Vínculo activado. */
    BINDING_ACTIVATED,

    /** Emisión del resultado verificable. */
    VERIFIABLE_RESULT_ISSUED,

    /** Vínculo bloqueado. */
    BINDING_BLOCKED,

    /** Vínculo desvinculado. */
    BINDING_UNBOUND
}
