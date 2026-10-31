package com.bcra.users.ws.user.entity;

import io.swagger.v3.oas.annotations.media.Schema;

/** Estados operativos del usuario bancario. */
@Schema(description = "Estado operativo: ACTIVE habilitante; SUSPENDED y LOGICAL_DELETE no habilitantes para H3")
public enum UserStatus {
    ACTIVE,
    SUSPENDED,
    /** Baja lógica que conserva el usuario y la reserva de su documento. */
    LOGICAL_DELETE
}
