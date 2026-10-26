package com.bcra.channel.ws.vinculo.exception;

import com.bcra.channel.ws.exception.LockedException;

/** Rechazo de dominio sin datos sensibles. */
public class VerificacionPausadaException extends LockedException {

    public VerificacionPausadaException() {
        super("Verificación pausada temporalmente. Contacte a soporte");
    }
}
