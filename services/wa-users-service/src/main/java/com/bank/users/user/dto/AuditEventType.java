package com.bank.users.user.dto;

/**
 * Tipo de evento de auditoría del ciclo de vida del usuario.
 */
public enum AuditEventType {
    /** Alta exitosa de un cliente. */
    ALTA,
    /** Cambio de estado (suspensión, reactivación, baja lógica); conserva estado anterior y nuevo. */
    CAMBIO_ESTADO,
    /** Actualización de datos permitidos; conserva solo los nombres de los campos modificados. */
    MODIFICACION_DATOS,
    /** Alta rechazada (p. ej. documento duplicado); no identifica a un usuario nuevo. */
    ALTA_RECHAZADA
}
