package com.wa.banking.users.api.v1.dto;

import com.wa.banking.users.entity.DocumentType;
import com.wa.banking.users.entity.UserStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.Map;

/**
 * Response DTO v1 acotado para consulta de usuario bancario (flujo U2).
 *
 * @author licius-it
 * @since 2026-06-22
 * @contact marceloalejandro.albarracin@gmail.com
 */
@Schema(description = "Representación de un usuario bancario")
public record UserResponseV1(
        @Schema(description = "Identificador interno", example = "665f1a2b3c4d5e6f7a8b9c0d")
        String id,

        @Schema(description = "Tipo de documento", example = "DNI")
        DocumentType documentType,

        @Schema(description = "Número de documento", example = "12345678")
        String documentNumber,

        @Schema(description = "Nombre para mostrar", example = "John Doe")
        String displayName,

        @Schema(description = "Correo electrónico", example = "john@example.com")
        String email,

        @Schema(description = "Teléfono de contacto", example = "+541112345678")
        String phone,

        @Schema(description = "Preferencias del usuario", example = "{\"lang\": \"es\"}")
        Map<String, Object> preferences,

        @Schema(description = "Estado operativo actual", example = "ACTIVE")
        UserStatus status,

        @Schema(description = "Indica si el usuario puede vincular canal (solo ACTIVE)", example = "true")
        boolean canLinkChannel,

        @Schema(description = "Fecha de creación", example = "2026-06-22T10:30:00Z")
        Instant createdAt,

        @Schema(description = "Fecha de última actualización", example = "2026-06-22T10:30:00Z")
        Instant updatedAt
) {
}
