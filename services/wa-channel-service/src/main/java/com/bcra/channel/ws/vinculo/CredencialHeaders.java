package com.bcra.channel.ws.vinculo;

/**
 * Headers que el Orquestador/H2 propagan tras validar la credencial de sesión.
 * H3 no valida tokens: solo exige que el contexto esté presente.
 */
public final class CredencialHeaders {

    public static final String DOCUMENTO_TIPO = "X-Credencial-Documento-Tipo";
    public static final String DOCUMENTO_NUMERO = "X-Credencial-Documento-Numero";

    private CredencialHeaders() {
    }
}
