package com.bcra.channel.ws.vinculo.dto;

/**
 * Perfil permitido en el canal: contacto de H1 más preferencias locales, sin datos legales.
 * @param nombre nombre de pila
 * @param apellido apellido
 * @param nombreTratamiento forma de tratamiento; puede ser nulo
 * @param email correo de contacto; puede ser nulo
 * @param telefono teléfono de contacto; puede ser nulo
 * @param preferencias preferencias del canal
 */
public record PerfilCanalResponse(String nombre, String apellido, String nombreTratamiento, String email,
        String telefono, PreferenciasResponse preferencias) {

    @Override
    public String toString() {
        return "PerfilCanalResponse[datos omitidos]";
    }
}
