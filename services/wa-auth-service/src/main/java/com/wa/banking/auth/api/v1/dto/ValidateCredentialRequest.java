package com.wa.banking.auth.api.v1.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Solicitud de validación de credencial para servicios de productos (H4–H6).
 *
 * @author licius-it
 * @since 2026-06-23
 * @contact marceloalejandro.albarracin@gmail.com
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Solicitud de validación de credencial de sesión")
public class ValidateCredentialRequest {

    @NotBlank(message = "credentialId is required")
    @Schema(
            description = "Identificador de la credencial",
            example = "cred_abc123",
            requiredMode = Schema.RequiredMode.REQUIRED
    )
    private String credentialId;

    @NotBlank(message = "token is required")
    @Schema(
            description = "Token opaco presentado por el consumidor",
            example = "sess_xxx_placeholder",
            requiredMode = Schema.RequiredMode.REQUIRED
    )
    private String token;
}
