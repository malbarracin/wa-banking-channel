package com.bcra.users.ws.user.dto;

import java.time.LocalDate;
import com.bcra.users.ws.user.entity.DocumentType;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Size;

public record CreateUserRequest(@NotNull DocumentType tipoDocumento,
        @NotBlank @Size(max = 64) String numeroDocumento,
        @NotBlank @Size(max = 255) String nombreCompleto,
        @NotNull @Past LocalDate fechaNacimiento,
        @NotBlank @Email @Size(max = 255) String email,
        @NotBlank @Size(max = 64) String telefono) {
}
