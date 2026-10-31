package com.bcra.channel.ws.vinculo.client;

/** Puerto saliente de validación, renovación confirmable y revocación a H2. */
public interface CredencialesServiceClient {

    /** Obtiene un reemplazo repetible; H3 debe guardarlo antes de confirmar su recepción. */
    RenovacionCredencialResponse obtenerRenovacion(String credencialActual);

    /** Confirma el JWT nuevo ya guardado; ante timeout H3 conserva ambos y repite esta llamada. */
    void confirmarRenovacion(String credencialNueva);
    /** Valida con H2 la vigencia y pertenencia de la sesión al número solicitado. */
    boolean validar(String credencial, String numeroWhatsapp);

    /**
     * Solicita una única vez la revocación; registra fallos HTTP y de transporte sin propagarlos.
     * @return {@code true} si H2 aceptó la solicitud; {@code false} si falló o hubo timeout
     */
    boolean solicitarRevocacion(String numeroWhatsapp, String motivo);
}
