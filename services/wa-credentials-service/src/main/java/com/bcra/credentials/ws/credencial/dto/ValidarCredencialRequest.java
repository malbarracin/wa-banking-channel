package com.bcra.credentials.ws.credencial.dto;

/** JWT presentado por un servicio de productos; nunca debe incluirse en logs. */
public record ValidarCredencialRequest(String credencial) {

    @Override
    public String toString() {
        return "ValidarCredencialRequest[credencial=PROTEGIDA]";
    }
}
