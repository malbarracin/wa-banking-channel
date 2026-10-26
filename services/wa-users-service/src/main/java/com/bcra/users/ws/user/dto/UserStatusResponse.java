package com.bcra.users.ws.user.dto;

import com.bcra.users.ws.user.entity.UserStatus;

import io.swagger.v3.oas.annotations.media.Schema;

/** Documento y estado actual para que los servicios de canal evalúen la habilitación de nuevos vínculos. */
@Schema(description = "Documento y estado para evaluación de vínculos de canal (H3)")
public record UserStatusResponse(
        DocumentoResponse documento,
        @Schema(description = "Estado operativo", example = "SUSPENDED") UserStatus estado) {
}
