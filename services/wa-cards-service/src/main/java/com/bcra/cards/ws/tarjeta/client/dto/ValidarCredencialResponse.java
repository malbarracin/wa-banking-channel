package com.bcra.cards.ws.tarjeta.client.dto;

import java.time.Instant;

/**
 * Identidad confirmada por H2 para autorizar operaciones del titular.
 * @param vigente indica que H2 permite operar con la credencial
 * @param numeroWhatsapp número del vínculo en formato E.164
 * @param documentoTipo tipo documental del titular
 * @param documentoNumero número documental del titular
 * @param fechaExpiracion vencimiento UTC de la sesión
 */
public record ValidarCredencialResponse(boolean vigente, String numeroWhatsapp, String documentoTipo,
        String documentoNumero, Instant fechaExpiracion) {

    @Override
    public String toString() {
        return "ValidarCredencialResponse[vigente=" + vigente + "]";
    }
}
