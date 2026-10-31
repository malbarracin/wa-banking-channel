package com.bcra.insurance.ws.seguro.dto;

import com.bcra.insurance.ws.seguro.entity.EstadoSiniestro;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Estado del caso según el sistema de siniestros y la acción pendiente del cliente.
 *
 * @param numeroCaso identificador único del caso
 * @param estado estado informado por el sistema de origen
 * @param pendiente acción pendiente del cliente (sin montos ni datos médicos)
 */
@Schema(description = "Estado de un caso de siniestro propio del titular")
public record SiniestroResponse(
        @Schema(description = "Número de caso", example = "SIN-2026-000123") String numeroCaso,
        @Schema(description = "Estado informado por el sistema de siniestros", example = "DOCUMENTACION_PENDIENTE")
        EstadoSiniestro estado,
        @Schema(description = "Acción pendiente del cliente (sin montos ni datos médicos)",
                example = "Presentar informe pericial")
        String pendiente) {
}
