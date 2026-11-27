package com.bcra.users.ws.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Datos de contacto y nombre de tratamiento que admite la actualización. */
@Schema(description = "Actualización parcial; solo campos en bank.users.updatable-fields")
public record UpdateUserRequest(
        @Size(max = 255)
        @Schema(description = "Nombre de tratamiento", example = "Juan Pérez", maxLength = 255)
        String displayName,
        @Email @Size(min = 1, max = 255)
        @Schema(description = "Correo de contacto", example = "juan.perez@example.com",
                minLength = 1, maxLength = 255)
        String contactEmail,
        @Pattern(regexp = "\\+?[0-9]{7,15}")
        @Schema(description = "Teléfono de contacto (E.164 simplificado)", example = "+5491198765432",
                pattern = "\\+?[0-9]{7,15}")
        String contactPhone) {

    @Override
    public String toString() {
        return "UpdateUserRequest[redacted]";
    }
}
