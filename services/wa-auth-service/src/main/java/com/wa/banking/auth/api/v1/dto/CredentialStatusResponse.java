package com.wa.banking.auth.api.v1.dto;

import com.wa.banking.auth.entity.CredentialStatus;
import com.wa.banking.auth.entity.RevokeReason;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Estado y metadatos de una credencial sin exponer el token.
 *
 * @author licius-it
 * @since 2026-06-23
 * @contact marceloalejandro.albarracin@gmail.com
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Estado de credencial de sesión (sin token)")
public class CredentialStatusResponse {

    @Schema(description = "Identificador de la credencial", example = "cred-abc123")
    private String credentialId;

    @Schema(description = "Identificador del vínculo canal-cliente", example = "link-abc123")
    private String channelLinkId;

    @Schema(description = "Identificador del usuario bancario", example = "user-xyz789")
    private String bankUserId;

    @Schema(description = "Número de teléfono E.164", example = "+541112345678")
    private String phoneNumber;

    @Schema(description = "Estado de la credencial", example = "ACTIVE")
    private CredentialStatus status;

    @Schema(description = "Fecha de emisión UTC", example = "2026-06-23T10:00:00Z")
    private Instant issuedAt;

    @Schema(description = "Fecha de expiración UTC", example = "2026-06-24T10:00:00Z")
    private Instant expiresAt;

    @Schema(description = "Fecha de revocación UTC", example = "2026-06-23T12:00:00Z")
    private Instant revokedAt;

    @Schema(description = "Motivo de revocación", example = "BLOCK")
    private RevokeReason revokeReason;

    @Schema(description = "Cantidad de renovaciones", example = "0")
    private int renewalCount;
}
