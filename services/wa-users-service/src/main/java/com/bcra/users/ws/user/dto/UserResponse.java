package com.bcra.users.ws.user.dto;

import java.time.LocalDate;
import java.util.UUID;

import com.bcra.users.ws.user.entity.UserStatus;

/**
 * Datos completos del usuario para este hito.
 * El filtrado de campos por perfil queda pendiente de la definición de Compliance.
 * @param preferencias preferencias generales no sensibles almacenadas como texto
 */
public record UserResponse(UUID id, DocumentoResponse documento, String nombre, String apellido,
        LocalDate fechaNacimiento, String email, String telefono, String nombreTratamiento,
        String preferencias, UserStatus estado) {
}
