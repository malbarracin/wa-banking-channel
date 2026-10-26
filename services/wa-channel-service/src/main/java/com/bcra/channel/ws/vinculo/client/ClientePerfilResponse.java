package com.bcra.channel.ws.vinculo.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Proyección de H1 que descarta documento, nacimiento y otros datos legales. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ClientePerfilResponse(
        String nombre, String apellido, String nombreTratamiento, String email, String telefono) {

    @Override
    public String toString() {
        return "ClientePerfilResponse[datos omitidos]";
    }
}
