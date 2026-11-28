package com.bcra.channel.ws.binding.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Actualización de preferencias del canal sobre un vínculo activo.
 *
 * @param languagePreference   código de idioma (ej. {@code es}, {@code en})
 * @param notificationsEnabled activación de notificaciones
 */
@Schema(description = "Preferencias del canal a actualizar")
public record UpdatePreferencesRequest(
        @NotBlank
        @Pattern(regexp = "^[a-z]{2}(-[A-Za-z]{2})?$", message = "debe ser un código de idioma (ej. es, en)")
        @Schema(
                description = "Código de idioma ISO 639-1, opcionalmente con región",
                example = "es",
                requiredMode = Schema.RequiredMode.REQUIRED,
                pattern = "^[a-z]{2}(-[A-Za-z]{2})?$")
        String languagePreference,
        @NotNull
        @Schema(
                description = "true habilita notificaciones del canal",
                example = "true",
                requiredMode = Schema.RequiredMode.REQUIRED)
        Boolean notificationsEnabled) {
}
