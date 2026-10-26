package com.bcra.users.ws.user.entity;

/** Estados operativos del usuario bancario. */
public enum UserStatus {
    ACTIVE,
    SUSPENDED,
    /** Baja lógica que conserva el usuario y la reserva de su documento. */
    LOGICAL_DELETE
}
