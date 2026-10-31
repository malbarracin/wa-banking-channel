package com.bcra.channel.ws.vinculo;

/**
 * Contrato del contexto de credencial que el orquestador/H2 propagan hacia H3.
 * El token de sesión debe validarse con H2 antes de autorizar una operación.
 */
public final class CredencialCanal {

    /** Header que transporta la credencial de sesión emitida por H2. */
    public static final String HEADER_CREDENCIAL_TOKEN = "X-Canal-Credencial-Token";

    private CredencialCanal() {
    }

}
