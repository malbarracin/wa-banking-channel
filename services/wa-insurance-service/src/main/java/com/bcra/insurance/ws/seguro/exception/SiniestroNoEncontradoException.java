package com.bcra.insurance.ws.seguro.exception;

import com.bcra.insurance.ws.exception.NotFoundException;

/** El número de caso no existe en el sistema de siniestros. */
public class SiniestroNoEncontradoException extends NotFoundException {

    public SiniestroNoEncontradoException() {
        super("El caso de siniestro no existe.");
    }
}
