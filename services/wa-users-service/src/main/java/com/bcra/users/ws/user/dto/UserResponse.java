package com.bcra.users.ws.user.dto;

import java.time.Instant;
import java.util.UUID;

import com.bcra.users.ws.user.entity.UserStatus;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

/** Datos autorizados por la política de privacidad y estado operativo. */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Cliente con los campos públicos configurados en bank.users.public-fields")
public record UserResponse(
        @Schema(description = "Identificador interno", example = "123e4567-e89b-12d3-a456-426614174000")
        UUID id,
        @Schema(description = "Identificador externo asignado", example = "USR-00042")
        String externalId,
        @Schema(description = "Valor de unicidad (solo si está en public-fields)", example = "CLI-1001")
        String uniquenessValue,
        @Schema(description = "Nombre de tratamiento", example = "Ana Pérez")
        String displayName,
        @Schema(description = "Correo de contacto", example = "ana.perez@example.com")
        String contactEmail,
        @Schema(description = "Teléfono de contacto", example = "+5491112345678")
        String contactPhone,
        @Schema(description = "Estado operativo", example = "ACTIVE")
        UserStatus status,
        @Schema(description = "Alta en UTC (ISO-8601)", example = "2026-11-27T12:00:00Z")
        Instant createdAt) {

    @Override
    public String toString() {
        return "UserResponse[redacted]";
    }
}
