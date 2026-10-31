package com.bcra.credentials.ws.credencial.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Estado bancario, independiente del estado de la credencial o del vínculo. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ClienteEstadoResponse(EstadoCliente estado) {

    /** Estados publicados por el contrato de H1. */
    public enum EstadoCliente {
        ACTIVE, SUSPENDED, LOGICAL_DELETE
    }
}
