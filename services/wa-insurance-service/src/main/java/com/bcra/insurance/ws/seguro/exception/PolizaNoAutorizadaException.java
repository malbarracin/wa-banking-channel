package com.bcra.insurance.ws.seguro.exception;

import com.bcra.insurance.ws.exception.ForbiddenException;

/** El cliente no es titular ni asegurado. */
public class PolizaNoAutorizadaException extends ForbiddenException {

    public PolizaNoAutorizadaException() {
        super("Consultá al titular o acudí a un canal formal para operar sobre esta póliza.");
    }
}
