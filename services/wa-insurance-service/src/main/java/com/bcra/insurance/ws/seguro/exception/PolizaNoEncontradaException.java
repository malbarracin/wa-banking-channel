package com.bcra.insurance.ws.seguro.exception;

import com.bcra.insurance.ws.exception.NotFoundException;

/** La póliza solicitada no existe. */
public class PolizaNoEncontradaException extends NotFoundException {

    public PolizaNoEncontradaException() {
        super("La póliza no existe.");
    }
}
