package com.bcra.channel.ws.vinculo.dto;

import com.bcra.channel.ws.vinculo.entity.VinculoEstado;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Consulta anti-duplicados: informa si el número tiene vínculo, sin datos del cliente.
 * @param numeroWhatsapp número consultado
 * @param vinculado {@code true} si existe registro en cualquier estado
 * @param estado estado del vínculo más reciente; nulo si no hay vínculo
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record VinculoEstadoResponse(String numeroWhatsapp, boolean vinculado, VinculoEstado estado) {
}
