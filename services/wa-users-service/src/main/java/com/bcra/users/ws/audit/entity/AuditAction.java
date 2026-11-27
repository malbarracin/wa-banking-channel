package com.bcra.users.ws.audit.entity;

import io.swagger.v3.oas.annotations.media.Schema;

/** Acción registrada en el historial de auditoría. */
@Schema(description = "Acción auditada", allowableValues = {"ALTA", "MODIFICACION", "CAMBIO_ESTADO"})
public enum AuditAction {
    /** Alta de cliente. */
    ALTA,
    /** Modificación de datos permitidos. */
    MODIFICACION,
    /** Cambio de estado operativo. */
    CAMBIO_ESTADO
}
