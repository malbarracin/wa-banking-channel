package com.bank.users.user.dto;

import java.time.LocalDate;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import com.fasterxml.jackson.annotation.JsonIgnore;

import io.swagger.v3.oas.annotations.media.Schema;

/** Datos mínimos del alta; se requiere al menos un contacto válido. */
public record CreateUserRequest(
        @NotBlank @Pattern(regexp = "DNI|PASSPORT|LC|LE|CUIT")
        @Schema(description = "Tipo de documento de identidad", example = "DNI",
                allowableValues = {"DNI", "PASSPORT", "LC", "LE", "CUIT"},
                requiredMode = Schema.RequiredMode.REQUIRED)
        String documentType,

        @NotBlank @Size(max = 64) @Pattern(regexp = "[A-Za-z0-9]+")
        @Schema(description = "Número de documento, alfanumérico sin espacios", example = "30111222",
                maxLength = 64, requiredMode = Schema.RequiredMode.REQUIRED)
        String documentNumber,

        @NotBlank @Size(max = 120)
        @Schema(description = "Nombre para tratamiento del usuario", example = "Ana Pérez",
                maxLength = 120, requiredMode = Schema.RequiredMode.REQUIRED)
        String displayName,

        @Email @Size(max = 254) @Pattern(regexp = "(?s).*\\S.*")
        @Schema(description = "Dirección de email de contacto", example = "ana@example.com",
                maxLength = 254, nullable = true)
        String email,

        @Pattern(regexp = "\\+?[0-9][0-9 ()-]{5,30}[0-9]")
        @Schema(description = "Teléfono de contacto con formato internacional", example = "+5491112345678",
                nullable = true, pattern = "\\+?[0-9][0-9 ()-]{5,30}[0-9]")
        String phone,

        @NotNull @Past
        @Schema(description = "Fecha de nacimiento; debe ser una fecha pasada (ISO-8601)",
                example = "1990-01-02", requiredMode = Schema.RequiredMode.REQUIRED)
        LocalDate birthDate) {

    /** Indica si se proporcionó al menos un contacto válido (email o teléfono). */
    @AssertTrue(message = "Debe indicar al menos email o teléfono")
    @JsonIgnore
    public boolean isContactPresent() {
        return (email != null && !email.isBlank()) || (phone != null && !phone.isBlank());
    }

    @Override
    public String toString() {
        return "CreateUserRequest[datos personales omitidos]";
    }
}
