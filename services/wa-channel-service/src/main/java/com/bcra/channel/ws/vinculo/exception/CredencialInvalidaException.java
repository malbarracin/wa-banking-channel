package com.bcra.channel.ws.vinculo.exception;

import com.bcra.channel.ws.exception.UnauthorizedException;

/** Falta el contexto de credencial validado aguas arriba (HTTP 401). */
public class CredencialInvalidaException extends UnauthorizedException {

    public CredencialInvalidaException() {
        super("Se requiere una credencial válida");
    }
}
