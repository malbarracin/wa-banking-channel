package com.bank.users.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Datos mínimos del alta H1; el contacto es opcional y no vincula un canal. */
public record CreateUserRequest(
        @NotBlank @Size(max = 255) String tipoDocumento,
        @NotBlank @Size(max = 255) String numeroDocumento,
        @NotBlank @Size(max = 255) String nombreTratamiento,
        @Email @Size(max = 255) String email,
        @Size(max = 255) String telefonoContacto) {
}
