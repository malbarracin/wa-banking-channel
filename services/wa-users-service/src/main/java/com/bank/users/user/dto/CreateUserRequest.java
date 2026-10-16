package com.bank.users.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Datos mínimos del alta H1; el contacto es opcional y no vincula un canal.
 */
@Schema(description = "Solicitud de alta de usuario bancario")
public record CreateUserRequest(
        @Schema(description = "Tipo de documento de identidad", example = "DNI",
                requiredMode = Schema.RequiredMode.REQUIRED, maxLength = 255)
        @NotBlank @Size(max = 255) String tipoDocumento,
        @Schema(description = "Número de documento; único junto con el tipo", example = "30111222",
                requiredMode = Schema.RequiredMode.REQUIRED, maxLength = 255)
        @NotBlank @Size(max = 255) String numeroDocumento,
        @Schema(description = "Nombre para tratamiento comercial", example = "Ana Pérez",
                requiredMode = Schema.RequiredMode.REQUIRED, maxLength = 255)
        @NotBlank @Size(max = 255) String nombreTratamiento,
        @Schema(description = "Correo de contacto opcional", example = "ana.perez@example.com",
                maxLength = 255)
        @Email @Size(max = 255) String email,
        @Schema(description = "Teléfono de contacto opcional; no es vínculo WhatsApp",
                example = "+5491112345678", maxLength = 255)
        @Size(max = 255) String telefonoContacto) {
}
