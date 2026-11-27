package com.bcra.channel.ws.config;

import java.util.Locale;

import org.springframework.context.MessageSource;
import org.springframework.stereotype.Component;

/**
 * Catálogo de mensajes textuales al cliente (sección 8).
 * Nunca incluye bindingId, clientId, tokens ni datos de verificación.
 */
@Component
public class ChannelMessages {

    private static final Locale LOCALE = Locale.forLanguageTag("es");

    private final MessageSource messageSource;
    private final BankChannelProperties properties;

    public ChannelMessages(MessageSource messageSource, BankChannelProperties properties) {
        this.messageSource = messageSource;
        this.properties = properties;
    }

    /** 8.2 — no aceptación de condiciones. */
    public String termsRejected() {
        return resolve("channel.message.terms.rejected");
    }

    /** 8.4 — cliente no encontrado; deriva al alta. */
    public String clientNotFound() {
        return resolve(
                "channel.message.client.not.found",
                properties.getName(),
                properties.getOnboardingUrl());
    }

    /** 8.5 — verificación fallida con intentos restantes. */
    public String verificationFailed(int remainingAttempts) {
        return resolve(
                "channel.message.verification.failed",
                remainingAttempts,
                properties.getSupportPhone());
    }

    /** 8.6 — intentos agotados; deriva a soporte. */
    public String verificationLocked() {
        return resolve("channel.message.verification.locked", properties.getSupportPhone());
    }

    /** 8.7 — número ya vinculado a otro cliente. */
    public String numberAlreadyLinked() {
        return resolve("channel.message.number.already.linked", properties.getSupportPhone());
    }

    /** 8.8 — bienvenida tras vinculación exitosa. */
    public String welcome(String clientName) {
        return resolve("channel.message.welcome", clientName);
    }

    /** 8.9 — cliente suspendido o dado de baja. */
    public String clientSuspended() {
        return resolve("channel.message.client.suspended", properties.getSupportPhone());
    }

    /** 8.10 — re-verificación por credencial expirada. */
    public String credentialReverify() {
        return resolve("channel.message.credential.reverify");
    }

    /** 8.10 — bloqueo del vínculo. */
    public String blocked() {
        return resolve("channel.message.blocked");
    }

    /** 8.10 — desvinculación confirmada. */
    public String unbound() {
        return resolve("channel.message.unbound");
    }

    private String resolve(String code, Object... args) {
        return messageSource.getMessage(code, args, LOCALE);
    }
}
