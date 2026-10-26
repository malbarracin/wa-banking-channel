package com.bcra.channel.ws.vinculo.exception;

import com.bcra.channel.ws.exception.NotFoundException;

/** Indica que el número consultado no tiene un vínculo registrado. */
public class VinculoNotFoundException extends NotFoundException {

    public VinculoNotFoundException() {
        super("No existe un vínculo registrado para el número consultado");
    }
}
