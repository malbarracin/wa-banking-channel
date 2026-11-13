package com.bcra.ws.users.domain;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Estados del ciclo de vida del usuario bancario.
 * <p>
 * Elegibilidad de vínculo canal WhatsApp: solo {@link #ACTIVE} implica
 * {@code channelLinkEligible = true}. H1 no implementa WhatsApp; H3 debe respetar el flag.
 * </p>
 */
@Schema(description = "Estado operativo: ACTIVE habilita canal; LOGICALLY_DELETED es terminal")
public enum UserStatus {

    /** Cliente operativo; channelLinkEligible=true. */
    ACTIVE,
    /** Cliente suspendido; no habilita nuevos vínculos de canal. */
    SUSPENDED,
    /** Baja lógica terminal: no admite transiciones a otro estado. */
    LOGICALLY_DELETED
}
