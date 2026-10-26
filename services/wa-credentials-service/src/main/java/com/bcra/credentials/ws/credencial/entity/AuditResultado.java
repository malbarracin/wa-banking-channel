package com.bcra.credentials.ws.credencial.entity;

import io.swagger.v3.oas.annotations.media.Schema;

/** Resultado de la operación auditada. */
@Schema(description = "EXITOSO: operación confirmada; RECHAZADO: solicitud rechazada.")
public enum AuditResultado {
    EXITOSO, RECHAZADO
}

