package com.bcra.channel.ws.vinculo.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.databind.JsonNode;

import com.bcra.channel.ws.vinculo.exception.PropiedadDesconocidaException;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Cambios de preferencias del canal. Campos ausentes o {@code null} conservan el valor actual.
 *
 * @param idioma idioma preferido del canal; null omite el cambio
 * @param notificacionesHabilitadas habilitación de notificaciones; null omite el cambio
 * @param horarioPreferido franja horaria opcional; null omite el cambio
 */
@Schema(description = "Solo idioma, notificacionesHabilitadas y horarioPreferido")
public record ActualizarPreferenciasRequest(
        @Schema(description = "Idioma preferido; null omite el cambio", example = "es") String idioma,
        @Schema(description = "Si las notificaciones están habilitadas; null omite el cambio",
                example = "true") Boolean notificacionesHabilitadas,
        @Schema(description = "Horario preferido opcional; null omite el cambio",
                example = "09:00-18:00") String horarioPreferido) {

    /** Rechaza cualquier propiedad fuera del contrato, incluso si Jackson ignora desconocidas. */
    @JsonAnySetter
    public void rechazarCampo(String campo, JsonNode valor) {
        throw new PropiedadDesconocidaException();
    }
}
