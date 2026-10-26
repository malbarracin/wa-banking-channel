package com.bcra.credentials.ws.credencial.exception;

import com.bcra.credentials.ws.exception.NotFoundException;

/** No hay una credencial activa para revocar. */
public class CredencialNoEncontradaException extends NotFoundException {

    public CredencialNoEncontradaException() {
        super("No existe una credencial activa");
    }
}
