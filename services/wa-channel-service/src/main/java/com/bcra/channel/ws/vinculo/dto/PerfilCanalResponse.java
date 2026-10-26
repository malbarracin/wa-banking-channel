package com.bcra.channel.ws.vinculo.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Perfil permitido en el canal: contacto de H1 más preferencias locales, sin datos legales.
 * @param nombre nombre de pila
 * @param apellido apellido
 * @param nombreTratamiento forma de tratamiento; puede ser nulo
 * @param email correo de contacto; puede ser nulo
 * @param telefono teléfono de contacto; puede ser nulo
 * @param preferencias preferencias del canal
 */
@Schema(description = "Perfil permitido del canal: datos de contacto de H1 y preferencias locales")
public record PerfilCanalResponse(
        @Schema(description = "Nombre de pila", example = "Ana") String nombre,
        @Schema(description = "Apellido", example = "Pérez") String apellido,
        @Schema(description = "Forma de tratamiento; puede ser null", example = "Anita") String nombreTratamiento,
        @Schema(description = "Email de contacto; puede ser null", example = "ana@example.test") String email,
        @Schema(description = "Teléfono de contacto; puede ser null", example = "5551234") String telefono,
        PreferenciasResponse preferencias) {

    @Override
    public String toString() {
        return "PerfilCanalResponse[datos omitidos]";
    }
}
