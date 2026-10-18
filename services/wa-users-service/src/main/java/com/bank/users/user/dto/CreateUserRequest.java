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

/** Datos mínimos del alta; se requiere al menos un contacto válido. */
public record CreateUserRequest(
        @NotBlank @Pattern(regexp = "DNI|PASSPORT|LC|LE|CUIT") String documentType,
        @NotBlank @Size(max = 64) @Pattern(regexp = "[A-Za-z0-9]+") String documentNumber,
        @NotBlank @Size(max = 120) String displayName,
        @Email @Size(max = 254) @Pattern(regexp = "(?s).*\\S.*") String email,
        @Pattern(regexp = "\\+?[0-9][0-9 ()-]{5,30}[0-9]") String phone,
        @NotNull @Past LocalDate birthDate) {

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
