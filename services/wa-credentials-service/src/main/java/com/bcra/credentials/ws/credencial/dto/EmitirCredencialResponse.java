package com.bcra.credentials.ws.credencial.dto;

import java.time.Instant;

/** Credencial destinada exclusivamente al canal H3, nunca a mensajes visibles por el cliente final. */
public record EmitirCredencialResponse(String credencial, Instant fechaExpiracion) {

    @Override
    public String toString() {
        return "EmitirCredencialResponse[protegido]";
    }
}
