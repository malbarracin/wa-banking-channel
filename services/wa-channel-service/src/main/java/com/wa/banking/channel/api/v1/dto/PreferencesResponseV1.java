package com.wa.banking.channel.api.v1.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Preferencias del canal WhatsApp (F2).
 *
 * @author licius-it
 * @since 2026-06-23
 * @contact marceloalejandro.albarracin@gmail.com
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Preferencias del canal WhatsApp")
public class PreferencesResponseV1 {

    @Schema(description = "Idioma preferido", example = "es")
    private String language;

    @Schema(description = "Notificaciones habilitadas", example = "true")
    private boolean notificationsEnabled;

    @Schema(description = "Inicio de horario silencioso (HH:mm)", example = "22:00")
    private String quietHoursStart;

    @Schema(description = "Fin de horario silencioso (HH:mm)", example = "08:00")
    private String quietHoursEnd;
}
