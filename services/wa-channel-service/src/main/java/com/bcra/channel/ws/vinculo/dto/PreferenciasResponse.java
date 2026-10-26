package com.bcra.channel.ws.vinculo.dto;

/**
 * Preferencias propias del canal expuestas en el perfil.
 * @param idioma código de idioma preferido; puede ser nulo
 * @param notificacionesHabilitadas si el canal envía notificaciones
 * @param horarioPreferido franja horaria opcional
 */
public record PreferenciasResponse(String idioma, boolean notificacionesHabilitadas, String horarioPreferido) {
}
