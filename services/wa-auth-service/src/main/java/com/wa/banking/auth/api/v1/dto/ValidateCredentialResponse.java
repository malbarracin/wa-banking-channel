package com.wa.banking.auth.api.v1.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Resultado de validación de credencial de sesión.
 *
 * @author licius-it
 * @since 2026-06-23
 * @contact marceloalejandro.albarracin@gmail.com
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Resultado de validación de credencial")
public class ValidateCredentialResponse {

    @Schema(description = "Indica si la credencial es vigente y válida", example = "true")
    private boolean valid;

    @Schema(description = "Usuario bancario asociado (solo si valid=true)", example = "user-xyz789")
    private String bankUserId;

    @Schema(description = "Vínculo canal asociado (solo si valid=true)", example = "link-abc123")
    private String channelLinkId;

    @Schema(description = "Fecha de expiración (solo si valid=true)", example = "2026-06-24T10:00:00Z")
    private Instant expiresAt;
}
