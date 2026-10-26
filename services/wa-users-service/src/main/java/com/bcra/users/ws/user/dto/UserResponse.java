package com.bcra.users.ws.user.dto;

import java.time.LocalDate;
import java.util.UUID;

import com.bcra.users.ws.user.entity.UserStatus;

/** Datos del usuario; preferencias contiene texto general no sensible y puede estar ausente. */
public record UserResponse(UUID id, DocumentoDto documento, String nombre, String apellido,
        LocalDate fechaNacimiento, String email, String telefono, String nombreTratamiento,
        String preferencias, UserStatus estado) {
}
