package com.bcra.users.ws.user.entity;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Estado operativo del cliente.
 * Solo {@link #ACTIVE} es elegible para un vínculo de canal nuevo.
 */
@Schema(description = "Estado operativo: ACTIVE elegible para vínculo de canal; "
        + "SUSPENDED e INACTIVE no admiten vínculo nuevo",
        allowableValues = {"ACTIVE", "SUSPENDED", "INACTIVE"})
public enum UserStatus {
    /** Cliente operativo; único estado elegible para vínculo de canal nuevo. */
    ACTIVE,
    /** Cliente suspendido temporalmente. */
    SUSPENDED,
    /** Baja lógica; no admite vínculo de canal nuevo. */
    INACTIVE
}
