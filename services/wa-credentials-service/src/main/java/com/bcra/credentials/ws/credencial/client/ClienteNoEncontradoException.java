package com.bcra.credentials.ws.credencial.client;

import com.bcra.credentials.ws.exception.NotFoundException;

/** H1 confirmó que el cliente consultado no existe. */
public class ClienteNoEncontradoException extends NotFoundException {

    public ClienteNoEncontradoException() {
        super("El cliente no existe");
    }
}
