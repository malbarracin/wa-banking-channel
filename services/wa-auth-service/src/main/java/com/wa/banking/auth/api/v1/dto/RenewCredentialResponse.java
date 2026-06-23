package com.wa.banking.auth.api.v1.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Respuesta de renovación de credencial de sesión activa.
 *
 * @author licius-it
 * @since 2026-06-23
 * @contact marceloalejandro.albarracin@gmail.com
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Credencial renovada con nueva fecha de expiración")
public class RenewCredentialResponse {

    @Schema(description = "Identificador de la credencial", example = "cred-abc123")
    private String credentialId;

    @Schema(description = "Nueva fecha de expiración UTC", example = "2026-06-25T10:00:00Z")
    private Instant expiresAt;

    @Schema(description = "Cantidad de renovaciones realizadas", example = "1")
    private int renewalCount;
}
