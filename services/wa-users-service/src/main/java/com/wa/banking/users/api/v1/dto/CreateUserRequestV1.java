package com.wa.banking.users.api.v1.dto;

import com.wa.banking.users.entity.DocumentType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.Map;

/**
 * Request DTO v1 para alta de usuario bancario (flujo U1).
 *
 * @author licius-it
 * @since 2026-06-22
 * @contact marceloalejandro.albarracin@gmail.com
 */
@Schema(description = "Datos requeridos para alta de usuario bancario (U1)")
public record CreateUserRequestV1(
        @Schema(
                description = "Tipo de documento de identidad",
                example = "DNI",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        @NotNull(message = "documentType is required")
        DocumentType documentType,

        @Schema(
                description = "Número de documento (único por tipo)",
                example = "12345678",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        @NotBlank(message = "documentNumber is required")
        String documentNumber,

        @Schema(
                description = "Nombre para mostrar del usuario",
                example = "John Doe",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        @NotBlank(message = "displayName is required")
        String displayName,

        @Schema(
                description = "Correo electrónico del usuario",
                example = "john@example.com",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        @NotBlank(message = "email is required")
        @Email(message = "email must be valid")
        String email,

        @Schema(
                description = "Teléfono de contacto en formato internacional",
                example = "+541112345678",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        @NotBlank(message = "phone is required")
        String phone,

        @Schema(
                description = "Preferencias opcionales del usuario (clave-valor)",
                example = "{\"lang\": \"es\"}"
        )
        Map<String, Object> preferences
) {
}
