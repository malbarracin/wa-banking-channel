package com.bcra.credentials.ws.credencial.entity;

import io.swagger.v3.oas.annotations.media.Schema;

/** Operación registrada sin almacenar el contenido de la credencial. */
@Schema(description = "EMISION: emisión; RENOVACION: renovación; REVOCACION: revocación; VALIDACION_FALLIDA: rechazo.")
public enum AuditAccion {
    EMISION, RENOVACION, REVOCACION, VALIDACION_FALLIDA
}

