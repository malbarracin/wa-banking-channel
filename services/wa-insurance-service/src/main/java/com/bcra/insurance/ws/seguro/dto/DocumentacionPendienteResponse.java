package com.bcra.insurance.ws.seguro.dto;

import java.util.List;

/**
 * Recordatorio de documentación pendiente con orientación de envío formal.
 *
 * @param documentos lista simple de documentos requeridos por el proceso formal
 * @param orientacionEnvio indicación de canales oficiales (el chat no reemplaza el trámite)
 */
public record DocumentacionPendienteResponse(List<String> documentos, String orientacionEnvio) {

    public DocumentacionPendienteResponse {
        documentos = List.copyOf(documentos);
    }
}
