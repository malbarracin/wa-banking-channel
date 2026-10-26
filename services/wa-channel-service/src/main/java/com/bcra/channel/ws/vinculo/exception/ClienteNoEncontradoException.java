package com.bcra.channel.ws.vinculo.exception;

import com.bcra.channel.ws.exception.NotFoundException;

/** Cliente inexistente en H1. */
public class ClienteNoEncontradoException extends NotFoundException {

    public ClienteNoEncontradoException() {
        super("Para operar por WhatsApp primero debe ser cliente del banco por otro canal");
    }
}
