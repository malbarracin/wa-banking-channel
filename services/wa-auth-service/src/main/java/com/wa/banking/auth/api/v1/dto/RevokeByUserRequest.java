package com.wa.banking.auth.api.v1.dto;

import com.wa.banking.auth.entity.RevokeReason;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Solicitud de revocación masiva de credenciales activas por usuario bancario.
 *
 * @author licius-it
 * @since 2026-06-23
 * @contact marceloalejandro.albarracin@gmail.com
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Revocación masiva por bankUserId")
public class RevokeByUserRequest {

    @NotBlank(message = "bankUserId is required")
    @Schema(
            description = "Identificador del usuario bancario",
            example = "user-xyz789",
            requiredMode = Schema.RequiredMode.REQUIRED
    )
    private String bankUserId;

    @Schema(description = "Motivo de revocación", example = "POLICY")
    private RevokeReason reason;
}
