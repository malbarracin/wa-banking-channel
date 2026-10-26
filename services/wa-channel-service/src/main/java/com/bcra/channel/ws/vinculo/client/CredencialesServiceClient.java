package com.bcra.channel.ws.vinculo.client;

/** Puerto saliente para solicitar revocación a H2 sin revertir cambios locales. */
public interface CredencialesServiceClient {

    /**
     * Solicita una única vez la revocación; registra fallos HTTP y de transporte sin propagarlos.
     * @return {@code true} si H2 aceptó la solicitud; {@code false} si falló o hubo timeout
     */
    boolean solicitarRevocacion(String numeroWhatsapp, String motivo);
}
