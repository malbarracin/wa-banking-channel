package com.bcra.channel.ws.vinculo.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Estado operativo de H1, independiente del estado del vínculo de canal. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ClienteEstadoResponse(EstadoCliente estado) {

    /** Valores publicados por H1, conservando su contrato JSON. */
    public enum EstadoCliente {
        ACTIVE, SUSPENDED, LOGICAL_DELETE
    }
}
