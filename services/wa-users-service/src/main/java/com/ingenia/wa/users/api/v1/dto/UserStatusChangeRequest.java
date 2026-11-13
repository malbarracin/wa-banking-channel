package com.ingenia.wa.users.api.v1.dto;

import com.ingenia.wa.users.domain.UserStatus;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request de cambio de estado (U4).
 *
 * @author Ingenia
 * @since 2026-10-19
 * Contacto: marcelo.albarracin@ingenia.la
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Estado objetivo del usuario (U4)")
public class UserStatusChangeRequest {

    @NotNull(message = "status is required")
    @Schema(
            description = "Estado destino permitido por política de transiciones",
            example = "SUSPENDED",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private UserStatus status;
}
