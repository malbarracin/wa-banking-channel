package com.bcra.insurance.ws.seguro.dto;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Recordatorio de documentación pendiente con orientación de envío formal.
 *
 * @param documentos lista simple de documentos requeridos por el proceso formal
 * @param orientacionEnvio indicación de canales oficiales (el chat no reemplaza el trámite)
 */
@Schema(description = "Documentos pendientes y orientación de envío formal")
public record DocumentacionPendienteResponse(
        @Schema(description = "Lista simple de documentos pendientes",
                example = "[\"Informe pericial\", \"Fotografías del siniestro\"]")
        List<String> documentos,
        @Schema(description = "Orientación a canales oficiales",
                example = "Enviá estos documentos por los canales oficiales del banco; este chat no reemplaza el proceso formal.")
        String orientacionEnvio) {

    public DocumentacionPendienteResponse {
        documentos = List.copyOf(documentos);
    }
}
