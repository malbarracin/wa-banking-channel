package com.bcra.users.ws.user.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Datos que pueden actualizarse; los campos omitidos o nulos no se modifican. */
public record UpdateUserRequest(
        @Pattern(regexp = ".*\\S.*") @Size(max = 255) String nombreTratamiento,
        @Email @Pattern(regexp = ".*\\S.*") @Size(max = 255) String email,
        @Pattern(regexp = ".*\\S.*") @Size(max = 64) String telefono,
        @Size(max = 2048) String preferencias) {
    /** Rechaza propiedades fuera del contrato sin conservar su valor. */
    @JsonAnySetter
    public void rejectUnknown(String name, Object value) {
        throw new UnknownUpdateFieldException();
    }

    /** Señala un campo no actualizable durante la lectura de JSON. */
    public static class UnknownUpdateFieldException extends RuntimeException {
    }
}
