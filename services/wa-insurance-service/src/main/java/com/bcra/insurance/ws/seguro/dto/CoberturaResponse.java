package com.bcra.insurance.ws.seguro.dto;

import java.util.List;

/** Resumen informativo; las condiciones generales oficiales prevalecen en caso de disputa. */
public record CoberturaResponse(List<String> coberturas, List<String> exclusiones, boolean resumen,
        String notaPrevalencia) {

    public CoberturaResponse {
        coberturas = List.copyOf(coberturas);
        exclusiones = List.copyOf(exclusiones);
    }
}
