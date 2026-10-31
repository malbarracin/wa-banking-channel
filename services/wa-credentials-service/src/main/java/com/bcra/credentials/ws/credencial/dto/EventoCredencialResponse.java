package com.bcra.credentials.ws.credencial.dto;

import java.time.Instant;

import io.swagger.v3.oas.annotations.media.Schema;

import com.bcra.credentials.ws.credencial.entity.AuditAccion;
import com.bcra.credentials.ws.credencial.entity.AuditResultado;

/** Evento resumido: motivo protegido en origen, sin token ni identificador de credencial. */
@Schema(description = "Evento resumido: motivo protegido en origen, sin token ni identificador de credencial.")
public record EventoCredencialResponse(
        @Schema(description = "Acción auditada: EMISION, RENOVACION, REVOCACION o VALIDACION_FALLIDA",
                example = "EMISION")
        AuditAccion accion,
        @Schema(description = "Resultado auditado: EXITOSO o RECHAZADO",
                example = "EXITOSO")
        AuditResultado resultado,
        @Schema(description = "Instante del evento UTC en formato ISO-8601",
                example = "2026-10-26T12:00:00Z")
        Instant fecha,
        @Schema(description = "Motivo protegido en origen, sin secretos; puede ser null",
                example = "EMISION")
        String motivo) {
}
