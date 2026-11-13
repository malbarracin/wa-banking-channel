package com.bcra.ws.users.domain;

/**
 * Estados del ciclo de vida del usuario bancario.
 * <p>
 * Elegibilidad de vínculo canal WhatsApp: solo {@link #ACTIVE} implica
 * {@code channelLinkEligible = true}. H1 no implementa WhatsApp; H3 debe respetar el flag.
 * </p>
 *
 * @since 2026-10-19
 */
public enum UserStatus {

    ACTIVE,
    SUSPENDED,
    LOGICALLY_DELETED
}
