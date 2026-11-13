package com.bcra.ws.users.api.v1.dto;

import com.bcra.ws.users.domain.DocumentType;
import com.bcra.ws.users.domain.UserStatus;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Subset de consulta para otros servicios del banco (sin datos de contacto).
 * Se usa cuando {@code X-Query-Actor} es {@code BANK_SERVICE}, está ausente o es desconocido.
 */
@Schema(description = "Existencia, documento, estado y elegibilidad del cliente")
public record UserBasicResponse(
        @Schema(description = "Id interno del usuario", example = "661f0a1b2c3d4e5f67890123")
        String id,
        @Schema(description = "Tipo de documento", example = "DNI")
        DocumentType documentType,
        @Schema(description = "Número de documento", example = "30111222")
        String documentNumber,
        @Schema(description = "Estado operativo", example = "ACTIVE")
        UserStatus status,
        @Schema(description = "true solo si status=ACTIVE", example = "true")
        boolean channelLinkEligible) {
}
