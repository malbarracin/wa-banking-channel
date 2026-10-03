package com.bank.users.user.entity;

/**
 * Estado del ciclo de vida de un usuario.
 */
public enum UserStatus {
    /** Cliente operativo; único estado elegible para iniciar vínculos de canal. */
    ACTIVE,
    /** Cliente suspendido temporalmente; puede reactivarse. */
    SUSPENDED,
    /** Baja lógica: el registro se conserva y su documento sigue reservado. */
    INACTIVE
}
