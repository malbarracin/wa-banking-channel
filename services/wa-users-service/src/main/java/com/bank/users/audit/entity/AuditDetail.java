package com.bank.users.audit.entity;

/** Motivos cerrados: nunca incorporar documentos, contactos ni datos del canal. */
public enum AuditDetail {
    OPERACION_CONFIRMADA,
    DATOS_INVALIDOS,
    USUARIO_DUPLICADO,
    USUARIO_NO_ENCONTRADO,
    TRANSICION_NO_PERMITIDA,
    CAMPOS_NO_PERMITIDOS
}
