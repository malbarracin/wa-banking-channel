package com.bcra.users.ws.audit.dto;

import java.time.Instant;
import java.util.UUID;

import com.bcra.users.ws.audit.entity.AuditAction;
import com.bcra.users.ws.audit.entity.AuditResult;
import com.bcra.users.ws.user.entity.UserStatus;
import io.swagger.v3.oas.annotations.media.Schema;

/** Entrada del historial de auditoría sin valores personales. */
public record AuditEntryResponse(
        @Schema(description = "Identificador de la entrada", example = "a1a2a3a4-1111-4444-8888-123456789abc") UUID id,
        @Schema(description = "Identificador del cliente", example = "b1b2b3b4-1111-4444-8888-123456789abc") UUID userId,
        @Schema(description = "Acción registrada", example = "ALTA") AuditAction action,
        @Schema(description = "Resultado de la operación", example = "OK") AuditResult result,
        @Schema(description = "Estado anterior", example = "ACTIVE") UserStatus previousStatus,
        @Schema(description = "Estado nuevo", example = "SUSPENDED") UserStatus newStatus,
        @Schema(description = "Nombres de campos modificados", example = "displayName") String changedFields,
        @Schema(description = "Identificador del actor", example = "service-account") String actor,
        @Schema(description = "Fecha de la operación en UTC", example = "2026-01-01T12:00:00Z") Instant occurredAt) {
}
