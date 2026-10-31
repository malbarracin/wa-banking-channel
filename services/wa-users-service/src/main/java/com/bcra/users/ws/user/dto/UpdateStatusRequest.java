package com.bcra.users.ws.user.dto;

import com.bcra.users.ws.user.entity.UserStatus;

import io.swagger.v3.oas.annotations.media.Schema;

/** Estado solicitado; debe estar presente y representar una transición soportada desde el estado actual. */
@Schema(description = "Estado destino solicitado para el usuario")
public record UpdateStatusRequest(
        @Schema(description = "Nuevo estado operativo", example = "SUSPENDED",
                requiredMode = Schema.RequiredMode.REQUIRED)
        UserStatus estado) {
}
