package com.bank.users.user.dto;

import java.time.Instant;

import com.bank.users.audit.entity.AuditAction;
import com.bank.users.audit.entity.AuditResult;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Evento resumido: fecha, acción, resultado y motivo controlado sin información personal.
 */
@Schema(description = "Evento de auditoría resumido sin PII ni datos de canal")
public record UserAuditResponse(
        @Schema(description = "Momento del evento en UTC ISO-8601", example = "2026-10-15T12:00:00Z")
        Instant occurredAt,
        @Schema(description = "Acción auditada", example = "ALTA",
                allowableValues = {"ALTA", "ACTUALIZACION", "CAMBIO_ESTADO"})
        AuditAction action,
        @Schema(description = "Resultado de la operación", example = "OK",
                allowableValues = {"OK", "RECHAZADO"})
        AuditResult result,
        @Schema(description = "Motivo controlado sin PII", example = "OPERACION_CONFIRMADA")
        String detail) {
}
