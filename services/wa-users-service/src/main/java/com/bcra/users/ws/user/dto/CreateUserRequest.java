package com.bcra.users.ws.user.dto;

import java.time.LocalDate;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import com.bcra.users.ws.user.validation.ContactoPresente;

import io.swagger.v3.oas.annotations.media.Schema;

/** Datos mínimos de alta; basta con informar email o teléfono, sin exigir un formato específico. */
@ContactoPresente
@Schema(description = "Alta de usuario: documento, identidad, fecha de nacimiento y al menos un contacto")
public record CreateUserRequest(
        @NotNull(message = "El documento es obligatorio") @Valid
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) DocumentoDto documento,
        @NotBlank(message = "El nombre es obligatorio")
        @Schema(description = "Nombre de pila", example = "Ana", requiredMode = Schema.RequiredMode.REQUIRED)
        String nombre,
        @NotBlank(message = "El apellido es obligatorio")
        @Schema(description = "Apellido", example = "Pérez", requiredMode = Schema.RequiredMode.REQUIRED)
        String apellido,
        @NotNull(message = "La fecha de nacimiento es obligatoria")
        @Schema(description = "Fecha de nacimiento ISO-8601", example = "1990-05-15",
                requiredMode = Schema.RequiredMode.REQUIRED)
        LocalDate fechaNacimiento,
        @Schema(description = "Email de contacto; formato no cerrado en H1", example = "ana@example.test")
        String email,
        @Schema(description = "Teléfono de contacto; formato no cerrado en H1", example = "5551234")
        String telefono) {
}
