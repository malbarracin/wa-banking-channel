package com.bcra.channel.ws.binding.entity;

/**
 * Estado del vínculo número WhatsApp ↔ cliente.
 * {@link #SIN_VINCULO} es virtual: ausencia de registro en base.
 */
public enum BindingStatus {

    /** Sin registro de vínculo (no se persiste). */
    SIN_VINCULO,

    /** Términos aceptados; pendiente de verificación de identidad. */
    PENDIENTE_VERIFICACION,

    /** Vínculo verificado y operativo. */
    ACTIVO,

    /** Acceso cortado temporalmente. */
    BLOQUEADO,

    /** Número desvinculado del canal. */
    DESVINCULADO
}
