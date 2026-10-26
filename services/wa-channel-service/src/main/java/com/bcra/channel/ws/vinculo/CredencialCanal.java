package com.bcra.channel.ws.vinculo;

/**
 * Contrato del contexto de credencial que el orquestador/H2 propagan hacia H3.
 * H3 no valida el token: solo exige que el marcador de credencial válida esté presente.
 */
public final class CredencialCanal {

    /** Header con valor {@code true} cuando la credencial ya fue verificada aguas arriba. */
    public static final String HEADER_CREDENCIAL_VALIDA = "X-Canal-Credencial-Valida";

    private CredencialCanal() {
    }

    /** Indica si el valor del header representa un contexto de credencial válido. */
    public static boolean esContextoValido(String valorHeader) {
        return valorHeader != null && valorHeader.equalsIgnoreCase("true");
    }
}
