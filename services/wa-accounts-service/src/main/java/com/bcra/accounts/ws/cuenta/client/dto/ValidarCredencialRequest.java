package com.bcra.accounts.ws.cuenta.client.dto;

/** Credencial interna enviada únicamente al endpoint de validación de H2. */
public record ValidarCredencialRequest(String credencial) {

    @Override
    public String toString() {
        return "ValidarCredencialRequest[credencial=PROTEGIDA]";
    }
}
