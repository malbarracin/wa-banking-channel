package com.bank.users.user.dto;

import java.time.LocalDate;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

import com.bank.users.user.entity.DocumentType;
import com.bank.users.user.entity.UserStatus;

/** Datos autorizados; channelLinkEligible se deriva del estado ACTIVE. */
public record UserResponse(
        @Schema(description = "Identificador interno único asignado en el alta",
                example = "550e8400-e29b-41d4-a716-446655440000")
        UUID id,

        @Schema(description = "Tipo de documento del usuario")
        DocumentType documentType,

        @Schema(description = "Número de documento sin espacios", example = "30111222")
        String documentNumber,

        @Schema(description = "Nombre para tratamiento", example = "Ana Pérez")
        String displayName,

        @Schema(description = "Dirección de email de contacto, nullable", example = "ana@example.com",
                nullable = true)
        String email,

        @Schema(description = "Teléfono de contacto, nullable", example = "+5491112345678", nullable = true)
        String phone,

        @Schema(description = "Fecha de nacimiento en formato ISO-8601 (yyyy-MM-dd)",
                example = "1990-01-02")
        LocalDate birthDate,

        @Schema(description = "Estado operativo del usuario")
        UserStatus status,

        @Schema(description = "Preferencias generales no sensibles del usuario")
        Map<String, Object> preferences,

        @Schema(description = "True solo cuando el usuario está ACTIVE y puede vincularse a un canal",
                example = "true")
        boolean channelLinkEligible) {

    public UserResponse {
        preferences = Collections.unmodifiableMap(new LinkedHashMap<>(preferences));
    }

    @Override
    public String toString() {
        return "UserResponse[id=" + id + ", status=" + status + "]";
    }
}
