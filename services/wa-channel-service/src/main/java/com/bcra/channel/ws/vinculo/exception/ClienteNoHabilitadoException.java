package com.bcra.channel.ws.vinculo.exception;

import com.bcra.channel.ws.exception.ConflictException;

/** Rechazo de dominio sin datos sensibles. */
public class ClienteNoHabilitadoException extends ConflictException {

    public ClienteNoHabilitadoException() {
        super("El cliente no está habilitado para operar por este canal");
    }
}
