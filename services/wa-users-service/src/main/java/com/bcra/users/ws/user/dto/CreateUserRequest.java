package com.bcra.users.ws.user.dto;

import java.time.LocalDate;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import com.bcra.users.ws.user.validation.ContactoPresente;

/** Datos mínimos de alta; basta con informar email o teléfono, sin exigir un formato específico. */
@ContactoPresente
public record CreateUserRequest(
        @NotNull(message = "El documento es obligatorio") @Valid DocumentoDto documento,
        @NotBlank(message = "El nombre es obligatorio") String nombre,
        @NotBlank(message = "El apellido es obligatorio") String apellido,
        @NotNull(message = "La fecha de nacimiento es obligatoria") LocalDate fechaNacimiento,
        String email,
        String telefono) {
}
