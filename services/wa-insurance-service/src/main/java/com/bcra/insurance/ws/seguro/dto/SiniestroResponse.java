package com.bcra.insurance.ws.seguro.dto;

import com.bcra.insurance.ws.seguro.entity.EstadoSiniestro;

/**
 * Estado del caso según el sistema de siniestros y la acción pendiente del cliente.
 *
 * @param numeroCaso identificador único del caso
 * @param estado estado informado por el sistema de origen
 * @param pendiente acción pendiente del cliente (sin montos ni datos médicos)
 */
public record SiniestroResponse(String numeroCaso, EstadoSiniestro estado, String pendiente) {
}
