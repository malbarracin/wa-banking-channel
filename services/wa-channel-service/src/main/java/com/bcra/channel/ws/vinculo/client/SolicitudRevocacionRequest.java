package com.bcra.channel.ws.vinculo.client;

/** Motivo enviado a H2, nunca registrado en logs del adaptador. */
public record SolicitudRevocacionRequest(String motivo) {

    @Override
    public String toString() {
        return "SolicitudRevocacionRequest[datos omitidos]";
    }
}
