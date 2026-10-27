package com.bcra.insurance.ws.seguro.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Confirmación de apertura con número de caso y orientación de próximos pasos.
 *
 * @param numeroCaso identificador único del caso abierto
 * @param proximosPasos orientación de documentación por canales oficiales
 * @param plazos referencia a los plazos del proceso formal (sin liquidación)
 */
@Schema(description = "Confirmación de denuncia con número de caso")
public record DenunciaResponse(
        @Schema(description = "Número de caso único", example = "SIN-2026-000123") String numeroCaso,
        @Schema(description = "Orientación de documentación por canales oficiales",
                example = "Enviá la documentación requerida por los canales oficiales del banco.")
        String proximosPasos,
        @Schema(description = "Referencia a plazos del proceso formal (sin liquidación)",
                example = "Los plazos dependen del proceso formal correspondiente a tu ramo; no prometemos liquidación por este canal.")
        String plazos) {
}
