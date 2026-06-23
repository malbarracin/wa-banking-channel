package com.wa.banking.channel.api.v1.dto;

import com.wa.banking.channel.entity.DocumentType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request para verificación de identidad del cliente (F1).
 *
 * @author licius-it
 * @since 2026-06-23
 * @contact marceloalejandro.albarracin@gmail.com
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Datos de verificación de identidad")
public class VerifyIdentityRequestV1 {

    @NotNull(message = "documentType is required")
    @Schema(
            description = "Tipo de documento",
            example = "DNI",
            requiredMode = Schema.RequiredMode.REQUIRED
    )
    private DocumentType documentType;

    @NotBlank(message = "documentNumber is required")
    @Schema(
            description = "Número de documento",
            example = "12345678",
            requiredMode = Schema.RequiredMode.REQUIRED
    )
    private String documentNumber;

    @NotBlank(message = "otpCode is required")
    @Schema(
            description = "Código OTP de verificación (MVP)",
            example = "123456",
            requiredMode = Schema.RequiredMode.REQUIRED
    )
    private String otpCode;
}
