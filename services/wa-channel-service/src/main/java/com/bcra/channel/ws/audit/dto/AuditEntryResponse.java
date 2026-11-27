package com.bcra.channel.ws.audit.dto;

import java.time.Instant;
import java.util.UUID;

import com.bcra.channel.ws.audit.entity.AuditAction;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Entrada de historial de auditoría expuesta a soporte.
 * No incluye datos de verificación ni secretos.
 */
@Schema(description = "Entrada resumida del historial de auditoría del vínculo")
public record AuditEntryResponse(
        @Schema(description = "Identificador de la entrada", example = "a1b2c3d4-e5f6-7890-abcd-ef1234567890")
        UUID id,

        @Schema(description = "Identificador del vínculo", example = "b2c3d4e5-f6a7-8901-bcde-f12345678901")
        UUID bindingId,

        @Schema(description = "Número WhatsApp en E.164", example = "+5491112345678")
        String whatsappNumber,

        @Schema(description = "Acción auditada", example = "TERMS_ACCEPTED")
        AuditAction action,

        @Schema(description = "Resultado de la acción", example = "OK", allowableValues = {"OK", "ERROR", "BLOQUEADO"})
        String result,

        @Schema(description = "Quién disparó la acción", example = "CLIENTE")
        String actor,

        @Schema(description = "Detalle sin secretos ni datos de verificación", example = "Términos aceptados")
        String detail,

        @Schema(description = "Momento del evento (UTC)", example = "2026-11-27T15:30:00Z")
        Instant occurredAt
) {

    @Override
    public String toString() {
        return "AuditEntryResponse[id=" + id
                + ", bindingId=" + bindingId
                + ", action=" + action
                + ", result=" + result
                + ", actor=" + actor
                + ", occurredAt=" + occurredAt
                + ']';
    }
}
