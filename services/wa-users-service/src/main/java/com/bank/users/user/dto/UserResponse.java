package com.bank.users.user.dto;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import com.bank.users.user.entity.UserStatus;

/** Datos autorizados del usuario; aptitud de canal derivada del estado operativo. */
public record UserResponse(UUID id, String tipoDocumento, String numeroDocumento, String nombreTratamiento,
        String email, String telefonoContacto, Map<String, Object> preferencias, UserStatus status,
        boolean aptoParaVinculoCanal, Instant createdAt, Instant updatedAt) {

    public UserResponse {
        preferencias = Map.copyOf(preferencias);
    }
}
