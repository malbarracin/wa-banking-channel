package com.bcra.channel.ws.binding.service;

import java.util.regex.Pattern;

import com.bcra.channel.ws.exception.BadRequestException;

/**
 * Validación de números WhatsApp en formato E.164.
 */
public final class WhatsappNumberFormat {

    private static final Pattern E164 = Pattern.compile("^\\+[1-9]\\d{1,14}$");

    private WhatsappNumberFormat() {
    }

    /**
     * Exige un número E.164 válido.
     *
     * @param whatsappNumber valor a validar
     * @throws BadRequestException si el formato es inválido
     */
    public static void requireValid(String whatsappNumber) {
        if (whatsappNumber == null || !E164.matcher(whatsappNumber).matches()) {
            throw new BadRequestException("Número de WhatsApp con formato inválido");
        }
    }
}
