package com.wa.banking.users.api.v1.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;

import java.util.Map;

/**
 * Request DTO v1 para actualización parcial de usuario bancario (flujo U3).
 * Solo campos mutables: displayName, email, phone, preferences.
 *
 * @author licius-it
 * @since 2026-06-22
 * @contact marceloalejandro.albarracin@gmail.com
 */
@Schema(description = "Campos mutables para actualización parcial de usuario (U3)")
public record UpdateUserRequestV1(
        @Schema(description = "Nombre para mostrar del usuario", example = "Jane Doe")
        String displayName,

        @Schema(description = "Correo electrónico del usuario", example = "jane@example.com")
        @Email(message = "email must be valid")
        String email,

        @Schema(description = "Teléfono de contacto", example = "+541198765432")
        String phone,

        @Schema(
                description = "Preferencias del usuario (clave-valor)",
                example = "{\"lang\": \"es\", \"notifications\": true}"
        )
        Map<String, Object> preferences
) {
}
