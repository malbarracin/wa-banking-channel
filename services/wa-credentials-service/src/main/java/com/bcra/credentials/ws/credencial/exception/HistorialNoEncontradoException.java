package com.bcra.credentials.ws.credencial.exception;

import com.bcra.credentials.ws.exception.NotFoundException;

/** No existe credencial persistida o no hay eventos asociados al número. */
public class HistorialNoEncontradoException extends NotFoundException {

    public HistorialNoEncontradoException() {
        super("No existe historial de credenciales para el número");
    }
}
