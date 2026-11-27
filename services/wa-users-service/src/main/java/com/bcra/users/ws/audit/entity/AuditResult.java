package com.bcra.users.ws.audit.entity;

import io.swagger.v3.oas.annotations.media.Schema;

/** Resultado de la operación auditada. */
@Schema(description = "Resultado de la operación")
public enum AuditResult {
    /** Operación aplicada con éxito. */
    OK,
    /** Operación rechazada o fallida. */
    ERROR
}
