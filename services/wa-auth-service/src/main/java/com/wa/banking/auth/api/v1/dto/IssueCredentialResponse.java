package com.wa.banking.auth.api.v1.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Respuesta de emisión de credencial; incluye el token opaco una sola vez.
 *
 * @author licius-it
 * @since 2026-06-23
 * @contact marceloalejandro.albarracin@gmail.com
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Credencial emitida con token opaco (solo en emisión)")
public class IssueCredentialResponse {

    @Schema(description = "Identificador de la credencial", example = "cred_abc123")
    private String credentialId;

    @Schema(
            description = "Token opaco de sesión (no reenviar al cliente en chat)",
            example = "sess_xxx_placeholder"
    )
    private String token;

    @Schema(description = "Fecha de expiración UTC", example = "2026-06-24T10:00:00Z")
    private Instant expiresAt;
}
