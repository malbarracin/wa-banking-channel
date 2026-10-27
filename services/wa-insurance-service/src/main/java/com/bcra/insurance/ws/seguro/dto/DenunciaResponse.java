package com.bcra.insurance.ws.seguro.dto;

/**
 * Confirmación de apertura con número de caso y orientación de próximos pasos.
 *
 * @param numeroCaso identificador único del caso abierto
 * @param proximosPasos orientación de documentación por canales oficiales
 * @param plazos referencia a los plazos del proceso formal (sin liquidación)
 */
public record DenunciaResponse(String numeroCaso, String proximosPasos, String plazos) {
}
