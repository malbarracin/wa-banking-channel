package com.bcra.insurance.ws.seguro.exception;

import com.bcra.insurance.ws.exception.ForbiddenException;

/** El caso no pertenece al titular autenticado. */
public class SiniestroNoAutorizadoException extends ForbiddenException {

    public SiniestroNoAutorizadoException() {
        super("Consultá al titular o acudí a un canal formal para operar sobre este caso.");
    }
}
