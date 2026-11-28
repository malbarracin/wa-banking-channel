package com.bcra.channel.ws.client;

import java.util.UUID;

/**
 * Resultado de consultar un cliente en el servicio de usuarios (H1).
 * Los campos de contacto solo se completan cuando el estado no es {@link UserLookupStatus#NOT_FOUND}.
 */
public record UserLookupResult(
        UserLookupStatus status,
        UUID id,
        String externalId,
        String displayName,
        String contactEmail,
        String contactPhone) {

    /**
     * Resultado cuando H1 responde 404.
     *
     * @return lookup sin datos de cliente
     */
    public static UserLookupResult notFound() {
        return new UserLookupResult(UserLookupStatus.NOT_FOUND, null, null, null, null, null);
    }

    @Override
    public String toString() {
        return "UserLookupResult[status=" + status + ", id=" + id + "]";
    }
}
