package com.bank.users.user.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.bank.users.user.entity.UserStatus;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Datos autorizados del usuario; aptitud de canal derivada del estado operativo.
 */
@Schema(description = "Representación autorizada del usuario bancario")
public record UserResponse(
        @Schema(description = "Identificador interno UUID", example = "a1b2c3d4-e5f6-7890-abcd-ef1234567890")
        UUID id,
        @Schema(description = "Tipo de documento", example = "DNI")
        String tipoDocumento,
        @Schema(description = "Número de documento", example = "30111222")
        String numeroDocumento,
        @Schema(description = "Nombre para tratamiento", example = "Ana Pérez")
        String nombreTratamiento,
        @Schema(description = "Correo de contacto", example = "ana.perez@example.com")
        String email,
        @Schema(description = "Teléfono de contacto", example = "+5491112345678")
        String telefonoContacto,
        @Schema(description = "Preferencias generales no sensibles", example = "{\"idioma\":\"es\"}")
        Map<String, Object> preferencias,
        @Schema(description = "Estado operativo", example = "ACTIVO",
                allowableValues = {"ACTIVO", "SUSPENDIDO", "BAJA_LOGICA"})
        UserStatus status,
        @Schema(description = "True solo si status es ACTIVO", example = "true")
        boolean aptoParaVinculoCanal,
        @Schema(description = "Alta en UTC ISO-8601", example = "2026-10-15T12:00:00Z")
        Instant createdAt,
        @Schema(description = "Última modificación en UTC ISO-8601", example = "2026-10-15T12:00:00Z")
        Instant updatedAt,
        @Schema(description = "Hasta 20 eventos recientes de auditoría sin PII de canal")
        List<UserAuditResponse> historialResumido) {

    public UserResponse {
        preferencias = Map.copyOf(preferencias);
        historialResumido = List.copyOf(historialResumido);
    }
}
