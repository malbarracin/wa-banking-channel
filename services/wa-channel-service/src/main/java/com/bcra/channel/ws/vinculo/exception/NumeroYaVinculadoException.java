package com.bcra.channel.ws.vinculo.exception;

import com.bcra.channel.ws.exception.ConflictException;

/** Rechazo de dominio sin datos sensibles. */
public class NumeroYaVinculadoException extends ConflictException {

    public NumeroYaVinculadoException() {
        super("El número ya está vinculado. Contacte a soporte");
    }
}
