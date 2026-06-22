package com.wa.banking.users.api.v1.dto;

import com.wa.banking.users.entity.UserStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/**
 * Request DTO v1 para cambio de estado de usuario bancario (flujo U4).
 *
 * @author licius-it
 * @since 2026-06-22
 * @contact marceloalejandro.albarracin@gmail.com
 */
@Schema(description = "Nuevo estado operativo solicitado para el usuario (U4)")
public record ChangeUserStatusRequestV1(
        @Schema(
                description = "Estado destino: ACTIVE, SUSPENDED o SOFT_DELETED",
                example = "SUSPENDED",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        @NotNull(message = "status is required")
        UserStatus status
) {
}
