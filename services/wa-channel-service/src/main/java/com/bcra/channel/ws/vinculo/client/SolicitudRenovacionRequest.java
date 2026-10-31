package com.bcra.channel.ws.vinculo.client;

/** Prueba de posesión del JWT, transmitida únicamente en el cuerpo de la petición interna a H2. */
public record SolicitudRenovacionRequest(String credencial) {

    @Override
    public String toString() {
        return "SolicitudRenovacionRequest[protegido]";
    }
}
