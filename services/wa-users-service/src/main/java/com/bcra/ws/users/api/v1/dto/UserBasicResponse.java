package com.bcra.ws.users.api.v1.dto;

import com.bcra.ws.users.domain.DocumentType;
import com.bcra.ws.users.domain.UserStatus;

import io.swagger.v3.oas.annotations.media.Schema;

/** Subset de consulta para otros servicios, sin datos de contacto ni identidad adicional. */
@Schema(description = "Existencia, documento, estado y elegibilidad del cliente")
public record UserBasicResponse(
        String id,
        DocumentType documentType,
        String documentNumber,
        UserStatus status,
        boolean channelLinkEligible) {
}
