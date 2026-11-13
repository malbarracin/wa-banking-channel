package com.ingenia.wa.users.api.v1.dto;

import java.time.Instant;
import java.util.Map;

import com.ingenia.wa.users.domain.AuditAction;
import com.ingenia.wa.users.domain.AuditResult;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Evento de auditoría expuesto por API.
 *
 * @author Ingenia
 * @since 2026-10-19
 * Contacto: marcelo.albarracin@ingenia.la
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Evento de auditoría de usuario")
public class UserAuditEventResponse {

    @Schema(description = "Id del evento", example = "66f0a1b2c3d4e5f678901234")
    private String id;

    @Schema(description = "Id interno del usuario", example = "661f0a1b2c3d4e5f67890123")
    private String userId;

    @Schema(description = "Acción auditada", example = "STATUS_CHANGED")
    private AuditAction action;

    @Schema(description = "Timestamp UTC ISO-8601", example = "2026-10-19T14:00:00Z")
    private Instant timestamp;

    @Schema(description = "Resultado de la acción", example = "SUCCESS")
    private AuditResult result;

    @Schema(
            description = "Resumen de campos tocados / transición de estado",
            example = "{\"from\":\"ACTIVE\",\"to\":\"SUSPENDED\"}")
    private Map<String, Object> summary;

    @Schema(description = "Actor (header X-Actor-Id o SYSTEM)", example = "ops")
    private String actor;
}
