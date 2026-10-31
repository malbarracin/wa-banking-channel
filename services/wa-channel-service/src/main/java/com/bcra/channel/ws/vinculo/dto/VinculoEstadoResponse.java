package com.bcra.channel.ws.vinculo.dto;

import com.bcra.channel.ws.vinculo.entity.VinculoEstado;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Consulta anti-duplicados: informa si el número tiene vínculo, sin datos del cliente.
 * @param numeroWhatsapp número consultado
 * @param vinculado {@code true} si existe registro en cualquier estado
 * @param estado estado del vínculo más reciente; nulo si no hay vínculo
 */
@Schema(description = "Consulta anti-duplicados sin datos del cliente; nunca devuelve 404")
public record VinculoEstadoResponse(
        @Schema(description = "Número consultado", example = "+5491112345678") String numeroWhatsapp,
        @Schema(description = "true si existe un vínculo en cualquier estado", example = "true") boolean vinculado,
        @Schema(description = "Estado del vínculo más reciente; se omite si no hay vínculo",
                example = "PENDIENTE_VERIFICACION") VinculoEstado estado) {
}
