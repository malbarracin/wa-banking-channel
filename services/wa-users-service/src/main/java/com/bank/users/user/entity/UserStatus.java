package com.bank.users.user.entity;

/** Estados operativos del usuario bancario en H1. */
public enum UserStatus {
    ACTIVO,
    SUSPENDIDO,
    /** Baja lógica conservada para consultas y unicidad del documento. */
    BAJA_LOGICA
}
