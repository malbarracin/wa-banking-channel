package com.bcra.channel.ws.vinculo.exception;

import com.bcra.channel.ws.exception.NotFoundException;

/** Rechazo de dominio sin datos sensibles. */
public class VinculoNoEncontradoException extends NotFoundException {

    public VinculoNoEncontradoException() {
        super("Debe aceptar los términos antes de verificar su identidad");
    }
}
