package com.bcra.users.ws.user.dto;

import com.bcra.users.ws.user.entity.UserStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Solicitud de cambio de estado operativo.
 *
 * @param status nuevo estado objetivo
 * @param actor  identificador del operador que solicita el cambio
 */
@Schema(description = "Cambio de estado operativo de un cliente")
public record ChangeUserStatusRequest(
        @NotNull @Schema(description = "Estado destino", example = "SUSPENDED", requiredMode = Schema.RequiredMode.REQUIRED)
        UserStatus status,
        @NotBlank @Schema(description = "Actor que solicita el cambio", example = "operador-123",
                requiredMode = Schema.RequiredMode.REQUIRED)
        String actor) {

    @Override
    public String toString() {
        return "ChangeUserStatusRequest[status=" + status + ", actor=redacted]";
    }
}
