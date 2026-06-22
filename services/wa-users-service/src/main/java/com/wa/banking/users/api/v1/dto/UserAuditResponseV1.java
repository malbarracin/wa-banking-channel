package com.wa.banking.users.api.v1.dto;

import com.wa.banking.users.entity.AuditAction;
import com.wa.banking.users.entity.UserStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;

/**
 * Response DTO v1 para entradas del historial de auditoría de un usuario.
 *
 * @author licius-it
 * @since 2026-06-22
 * @contact marceloalejandro.albarracin@gmail.com
 */
@Schema(description = "Entrada del historial de auditoría de un usuario")
public record UserAuditResponseV1(
        @Schema(description = "Identificador de la entrada de auditoría", example = "audit-001")
        String id,

        @Schema(description = "Identificador del usuario auditado", example = "665f1a2b3c4d5e6f7a8b9c0d")
        String userId,

        @Schema(description = "Acción registrada", example = "STATUS_CHANGED")
        AuditAction action,

        @Schema(description = "Estado anterior (null en CREATED)", example = "ACTIVE")
        UserStatus previousStatus,

        @Schema(description = "Estado nuevo (null en UPDATED)", example = "SUSPENDED")
        UserStatus newStatus,

        @Schema(description = "Campos modificados (solo en UPDATED)", example = "[\"displayName\", \"email\"]")
        List<String> changedFields,

        @Schema(description = "Fecha y hora de la operación", example = "2026-06-22T12:00:00Z")
        Instant performedAt,

        @Schema(description = "Resultado de la operación", example = "SUCCESS")
        String result
) {
}
