package com.wa.banking.auth.api.v1.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Solicitud de emisión de credencial de sesión tras verificación H3 (compat {@code linkId}).
 *
 * @author licius-it
 * @since 2026-06-23
 * @contact marceloalejandro.albarracin@gmail.com
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Solicitud de emisión de credencial de sesión")
public class IssueCredentialRequest {

    @NotBlank(message = "channelLinkId is required")
    @JsonAlias("linkId")
    @Schema(
            description = "Identificador del vínculo canal-cliente. En JSON también se acepta el alias `linkId`.",
            example = "link-abc123",
            requiredMode = Schema.RequiredMode.REQUIRED
    )
    private String channelLinkId;

    @NotBlank(message = "bankUserId is required")
    @Schema(
            description = "Identificador del usuario bancario",
            example = "user-xyz789",
            requiredMode = Schema.RequiredMode.REQUIRED
    )
    private String bankUserId;

    @Schema(description = "Número de teléfono E.164", example = "+541112345678")
    private String phoneNumber;

    @Schema(
            description = "Indica si la identidad fue verificada; debe ser true para emitir credencial",
            example = "true",
            requiredMode = Schema.RequiredMode.REQUIRED
    )
    private Boolean identityVerified;
}
