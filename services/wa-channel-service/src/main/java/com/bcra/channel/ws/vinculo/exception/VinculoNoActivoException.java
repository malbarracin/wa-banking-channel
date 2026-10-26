package com.bcra.channel.ws.vinculo.exception;

import com.bcra.channel.ws.exception.ConflictException;

/** El vínculo existe pero no está en estado ACTIVO. */
public class VinculoNoActivoException extends ConflictException {

    public VinculoNoActivoException() {
        super("El vínculo no está activo");
    }
}
