package com.bcra.cards.ws.tarjeta.exception;

import com.bcra.cards.ws.exception.NotFoundException;

/** Tarjeta inexistente o no disponible para el titular. */
public class TarjetaNoEncontradaException extends NotFoundException {

    public TarjetaNoEncontradaException() {
        super("No se encontró la tarjeta");
    }
}
