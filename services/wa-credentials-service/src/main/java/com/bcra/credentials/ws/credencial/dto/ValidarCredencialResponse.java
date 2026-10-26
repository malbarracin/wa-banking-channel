package com.bcra.credentials.ws.credencial.dto;

import java.time.Instant;

/** Identidad de la credencial vigente y vencimiento UTC. */
public record ValidarCredencialResponse(boolean vigente, String numeroWhatsapp, String documentoTipo,
        String documentoNumero, Instant fechaExpiracion) {

    @Override
    public String toString() {
        return "ValidarCredencialResponse[vigente=" + vigente + "]";
    }
}
