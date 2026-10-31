package com.bcra.insurance.ws.seguro.exception;

import com.bcra.insurance.ws.exception.ConflictException;

/** Existe una denuncia abierta de la misma póliza y tipo dentro de la ventana configurada. */
public class SiniestroDuplicadoException extends ConflictException {

    public SiniestroDuplicadoException() {
        super("Ya existe una denuncia abierta para esta póliza y tipo de evento.");
    }
}
