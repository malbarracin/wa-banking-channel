package com.bcra.users.ws.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

/** Datos del alta; los campos obligatorios dependen de la política del banco. */
@Schema(description = "Alta de cliente; obligatorios según bank.users.required-fields")
public record CreateUserRequest(
        @Size(max = 255)
        @Schema(description = "Valor del campo de unicidad del banco", example = "CLI-1001", maxLength = 255)
        String uniquenessValue,
        @Size(max = 255)
        @Schema(description = "Nombre de tratamiento", example = "Ana Pérez", maxLength = 255)
        String displayName,
        @Email @Size(max = 255)
        @Schema(description = "Correo de contacto", example = "ana.perez@example.com", maxLength = 255)
        String contactEmail,
        @Size(max = 255)
        @Schema(description = "Teléfono de contacto", example = "+5491112345678", maxLength = 255)
        String contactPhone) {

    @Override
    public String toString() {
        return "CreateUserRequest[redacted]";
    }
}
