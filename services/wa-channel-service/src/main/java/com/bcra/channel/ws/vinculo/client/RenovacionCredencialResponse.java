package com.bcra.channel.ws.vinculo.client;

import java.time.Instant;

/** Reemplazo que H3 debe guardar antes de confirmar; nunca incluir en mensajes al cliente. */
public record RenovacionCredencialResponse(String credencial, Instant fechaExpiracion) {

    @Override
    public String toString() {
        return "RenovacionCredencialResponse[protegido]";
    }
}
