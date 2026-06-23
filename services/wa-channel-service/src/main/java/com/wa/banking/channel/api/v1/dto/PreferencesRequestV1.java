package com.wa.banking.channel.api.v1.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request parcial para actualizar preferencias del canal (F2).
 *
 * @author licius-it
 * @since 2026-06-23
 * @contact marceloalejandro.albarracin@gmail.com
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Actualización parcial de preferencias del canal")
public class PreferencesRequestV1 {

    @Schema(description = "Idioma preferido", example = "es")
    private String language;

    @Schema(description = "Notificaciones habilitadas", example = "true")
    private Boolean notificationsEnabled;

    @Pattern(regexp = "^([01]\\d|2[0-3]):[0-5]\\d$", message = "quietHoursStart must be HH:mm")
    @Schema(description = "Inicio de horario silencioso (HH:mm)", example = "22:00")
    private String quietHoursStart;

    @Pattern(regexp = "^([01]\\d|2[0-3]):[0-5]\\d$", message = "quietHoursEnd must be HH:mm")
    @Schema(description = "Fin de horario silencioso (HH:mm)", example = "08:00")
    private String quietHoursEnd;
}
