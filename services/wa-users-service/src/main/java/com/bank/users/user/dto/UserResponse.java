package com.bank.users.user.dto;

import java.time.Instant;
import java.time.LocalDate;

import com.bank.users.user.entity.DocumentType;
import com.bank.users.user.entity.UserStatus;

/**
 * Proyección pública de un usuario, común a todos los actores H1.
 * No expone auditoría ni datos de canal.
 *
 * @param id             identificador interno
 * @param documentType   tipo de documento
 * @param documentNumber número de documento
 * @param firstName      nombre legal
 * @param lastName       apellido legal
 * @param displayName    nombre de tratamiento
 * @param email          correo electrónico
 * @param birthDate      fecha de nacimiento
 * @param status         estado del ciclo de vida
 * @param createdAt      instante de alta
 * @param updatedAt      instante de la última modificación
 */
public record UserResponse(
        Long id,
        DocumentType documentType,
        String documentNumber,
        String firstName,
        String lastName,
        String displayName,
        String email,
        LocalDate birthDate,
        UserStatus status,
        Instant createdAt,
        Instant updatedAt) {
}
