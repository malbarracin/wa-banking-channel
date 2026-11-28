package com.bcra.channel.ws.client;

/**
 * Resultado tipado de la consulta al servicio de usuarios (H1).
 */
public enum UserLookupStatus {

    /** Cliente encontrado con estado operativo ACTIVE. */
    FOUND_ACTIVE,

    /** Cliente encontrado con estado operativo SUSPENDED. */
    FOUND_SUSPENDED,

    /** Cliente encontrado con estado operativo INACTIVE. */
    FOUND_INACTIVE,

    /** H1 respondió 404: el cliente no existe. */
    NOT_FOUND
}
