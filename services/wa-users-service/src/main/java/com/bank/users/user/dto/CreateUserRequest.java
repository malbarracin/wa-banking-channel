package com.bank.users.user.dto;

import java.time.LocalDate;

import com.bank.users.user.entity.DocumentType;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Datos de alta de un cliente.
 *
 * @param documentType   tipo de documento (obligatorio)
 * @param documentNumber número de documento (obligatorio, no blanco)
 * @param firstName      nombre legal (obligatorio, no blanco)
 * @param lastName       apellido legal (obligatorio, no blanco)
 * @param displayName    nombre de tratamiento (opcional)
 * @param email          correo electrónico (opcional; con formato válido si se informa)
 * @param birthDate      fecha de nacimiento en formato ISO {@code yyyy-MM-dd} (obligatoria)
 */
public record CreateUserRequest(
        @NotNull DocumentType documentType,
        @NotBlank String documentNumber,
        @NotBlank String firstName,
        @NotBlank String lastName,
        String displayName,
        @Email String email,
        @NotNull LocalDate birthDate) {
}
