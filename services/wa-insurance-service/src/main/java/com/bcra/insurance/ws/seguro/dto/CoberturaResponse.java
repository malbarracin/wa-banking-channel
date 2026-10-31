package com.bcra.insurance.ws.seguro.dto;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

/** Resumen informativo; las condiciones generales oficiales prevalecen en caso de disputa. */
@Schema(description = "Resumen de coberturas y exclusiones (no reemplaza las condiciones oficiales)")
public record CoberturaResponse(
        @Schema(description = "Coberturas principales en lenguaje simple",
                example = "[\"Responsabilidad civil\", \"Daños propios\"]")
        List<String> coberturas,
        @Schema(description = "Exclusiones relevantes resumidas",
                example = "[\"Uso comercial no declarado\"]")
        List<String> exclusiones,
        @Schema(description = "Siempre true: indica que es un resumen informativo", example = "true")
        boolean resumen,
        @Schema(description = "Aclara prevalencia de condiciones generales oficiales",
                example = "Este es un resumen informativo. En caso de disputa prevalecen las condiciones generales oficiales de la póliza.")
        String notaPrevalencia) {

    public CoberturaResponse {
        coberturas = List.copyOf(coberturas);
        exclusiones = List.copyOf(exclusiones);
    }
}
