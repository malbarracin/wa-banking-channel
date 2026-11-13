package com.bcra.ws.users.api.v1.dto;

import java.time.Instant;

import com.bcra.ws.users.domain.DocumentType;
import com.bcra.ws.users.domain.UserStatus;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Respuesta autorizada de usuario (sin datos fuera de política).
 * {@code channelLinkEligible} es true solo si {@code status == ACTIVE}.
 *
 * @since 2026-10-19
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Datos autorizados del usuario + estado y elegibilidad de canal")
public class UserResponse {

    @Schema(description = "Id interno del usuario", example = "661f0a1b2c3d4e5f67890123")
    private String id;

    @Schema(description = "Tipo de documento", example = "DNI")
    private DocumentType documentType;

    @Schema(description = "Número de documento", example = "30111222")
    private String documentNumber;

    @Schema(description = "Nombre legal", example = "Maria")
    private String firstName;

    @Schema(description = "Apellido legal", example = "Lopez")
    private String lastName;

    @Schema(description = "Email de contacto", example = "maria.lopez@example.com")
    private String email;

    @Schema(description = "Teléfono de contacto", example = "+5491112345678")
    private String phone;

    @Schema(description = "Nombre preferido para tratamiento", example = "Mari")
    private String preferredName;

    @Schema(description = "Idioma preferido BCP-47", example = "es")
    private String preferredLanguage;

    @Schema(description = "Estado operativo", example = "ACTIVE")
    private UserStatus status;

    @Schema(
            description = "true solo si status=ACTIVE; H3 debe bloquear vínculo si es false",
            example = "true")
    private boolean channelLinkEligible;

    @Schema(description = "Fecha de alta (UTC)", example = "2026-10-19T12:00:00Z")
    private Instant createdAt;

    @Schema(description = "Última actualización (UTC)", example = "2026-10-19T12:00:00Z")
    private Instant updatedAt;
}
