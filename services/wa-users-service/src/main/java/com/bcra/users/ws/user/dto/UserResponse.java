package com.bcra.users.ws.user.dto;

import java.time.LocalDate;
import java.util.UUID;

import com.bcra.users.ws.user.entity.UserStatus;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Datos completos del usuario para este hito.
 * El filtrado de campos por perfil queda pendiente de la definición de Compliance.
 * @param preferencias preferencias generales no sensibles almacenadas como texto
 */
@Schema(description = "Usuario completo (provisional hasta filtrado por perfil de Compliance)")
public record UserResponse(
        @Schema(description = "Identificador asignado", example = "a1b2c3d4-e5f6-7890-abcd-ef1234567890")
        UUID id,
        DocumentoResponse documento,
        @Schema(description = "Nombre", example = "Ana") String nombre,
        @Schema(description = "Apellido", example = "Pérez") String apellido,
        @Schema(description = "Fecha de nacimiento ISO-8601", example = "1990-05-15") LocalDate fechaNacimiento,
        @Schema(description = "Email", example = "ana@example.test") String email,
        @Schema(description = "Teléfono", example = "5551234") String telefono,
        @Schema(description = "Nombre para tratamiento", example = "Anita") String nombreTratamiento,
        @Schema(description = "Preferencias generales no sensibles", example = "canal:whatsapp")
        String preferencias,
        @Schema(description = "Estado operativo actual", example = "ACTIVE") UserStatus estado) {
}
