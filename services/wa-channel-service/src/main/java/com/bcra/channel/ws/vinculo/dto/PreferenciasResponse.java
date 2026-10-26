package com.bcra.channel.ws.vinculo.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Preferencias públicas del canal asociadas al vínculo.
 *
 * @param idioma idioma configurado; puede ser null si aún no se eligió
 * @param notificacionesHabilitadas si el cliente recibe notificaciones del canal
 * @param horarioPreferido franja horaria opcional
 */
@Schema(description = "Preferencias del canal WhatsApp")
public record PreferenciasResponse(
        @Schema(description = "Idioma del canal", example = "es") String idioma,
        @Schema(description = "Notificaciones habilitadas", example = "true") boolean notificacionesHabilitadas,
        @Schema(description = "Horario preferido", example = "09:00-18:00") String horarioPreferido) {
}
