package com.bcra.users.ws.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Datos de contacto y nombre de tratamiento que admite la actualización. */
public record UpdateUserRequest(@Size(max = 255) String displayName,
        @Email @Size(min = 1, max = 255) String contactEmail,
        @Pattern(regexp = "\\+?[0-9]{7,15}") String contactPhone) {
    @Override
    public String toString() {
        return "UpdateUserRequest[redacted]";
    }
}
