package com.bcra.channel.ws.vinculo.client;

/** Puerto de validación autoritativa y solicitud de revocación a H2. */
public interface CredencialesServiceClient {

    /** Valida con H2 la vigencia y pertenencia de la sesión al número solicitado. */
    boolean validar(String credencial, String numeroWhatsapp);

    /**
     * Solicita una única vez la revocación; registra fallos HTTP y de transporte sin propagarlos.
     * @return {@code true} si H2 aceptó la solicitud; {@code false} si falló o hubo timeout
     */
    boolean solicitarRevocacion(String numeroWhatsapp, String motivo);
}
