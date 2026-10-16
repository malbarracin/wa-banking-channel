package com.bank.users.user.entity;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Estados operativos del usuario bancario en H1.
 */
@Schema(description = "Estado operativo del usuario",
        allowableValues = {"ACTIVO", "SUSPENDIDO", "BAJA_LOGICA"})
public enum UserStatus {
    /** Operativo y apto para vínculo de canal. */
    ACTIVO,
    /** Temporalmente inapto para nuevos vínculos hasta regularizar. */
    SUSPENDIDO,
    /** Baja lógica conservada para consultas y unicidad del documento; sin reactivación en H1. */
    BAJA_LOGICA
}
