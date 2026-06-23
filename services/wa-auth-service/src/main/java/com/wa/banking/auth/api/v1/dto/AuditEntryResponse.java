package com.wa.banking.auth.api.v1.dto;

import com.wa.banking.auth.entity.AuditAction;
import com.wa.banking.auth.entity.AuditActor;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Entrada del historial de auditoría de una credencial (sin secretos).
 *
 * @author licius-it
 * @since 2026-06-23
 * @contact marceloalejandro.albarracin@gmail.com
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Entrada de auditoría de credencial")
public class AuditEntryResponse {

    @Schema(description = "Identificador de la credencial", example = "cred-abc123")
    private String credentialId;

    @Schema(description = "Identificador del vínculo", example = "link-abc123")
    private String channelLinkId;

    @Schema(description = "Usuario bancario", example = "user-xyz789")
    private String bankUserId;

    @Schema(description = "Acción registrada", example = "ISSUED")
    private AuditAction action;

    @Schema(description = "Actor que ejecutó la acción", example = "CHANNEL")
    private AuditActor actor;

    @Schema(description = "Motivo o detalle adicional", example = "REPLACED")
    private String reason;

    @Schema(description = "Fecha de la acción UTC", example = "2026-06-23T10:00:00Z")
    private Instant performedAt;
}
