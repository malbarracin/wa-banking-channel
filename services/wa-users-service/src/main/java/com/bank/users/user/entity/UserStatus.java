package com.bank.users.user.entity;

import io.swagger.v3.oas.annotations.media.Schema;

/** Estado operativo; únicamente ACTIVE permite nuevos vínculos de canal. */
@Schema(description = "Estado operativo del usuario y política de transiciones",
        allowableValues = {"ACTIVE", "SUSPENDED", "LOGICALLY_DELETED"})
public enum UserStatus {
    /** Estado activo: apto para vínculo de canal. Transiciones permitidas: SUSPENDED, LOGICALLY_DELETED. */
    @Schema(description = "Activo — apto para vínculo de canal. Puede suspenderse o darse de baja.")
    ACTIVE,
    /** Estado suspendido: no apto para vínculo de canal. Transiciones permitidas: ACTIVE, LOGICALLY_DELETED. */
    @Schema(description = "Suspendido — no apto para vínculo de canal. Puede reactivarse o darse de baja.")
    SUSPENDED,
    /** Estado de baja lógica: terminal. No permite transiciones ni actualización de datos. */
    @Schema(description = "Baja lógica — estado terminal, no apto para vínculo. No admite transiciones.")
    LOGICALLY_DELETED
}
