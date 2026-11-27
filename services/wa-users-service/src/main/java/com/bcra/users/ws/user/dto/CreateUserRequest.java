package com.bcra.users.ws.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

/** Datos del alta; los campos obligatorios dependen de la política del banco. */
public record CreateUserRequest(@Size(max = 255) String uniquenessValue, @Size(max = 255) String displayName,
        @Email @Size(max = 255) String contactEmail, @Size(max = 255) String contactPhone) {
    @Override
    public String toString() {
        return "CreateUserRequest[redacted]";
    }
}
