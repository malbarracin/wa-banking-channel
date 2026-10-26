package com.bcra.channel.ws.vinculo.client;

/** Puerto saliente para solicitar revocación a H2 sin revertir cambios locales. */
public interface CredencialesServiceClient {

    /** Solicita una única vez la revocación; registra fallos HTTP y de transporte sin propagarlos. */
    void solicitarRevocacion(String numeroWhatsapp, String motivo);
}
