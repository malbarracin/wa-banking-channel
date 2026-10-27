package com.bcra.insurance.ws.seguro.entity;

import io.swagger.v3.oas.annotations.media.Schema;

/** Tipos de evento admitidos para una denuncia. */
@Schema(description = "Tipo de siniestro admitido en la denuncia",
        allowableValues = {"HOGAR", "AUTO", "VIDA", "OTROS"})
public enum TipoSiniestro {
    HOGAR, AUTO, VIDA, OTROS
}
