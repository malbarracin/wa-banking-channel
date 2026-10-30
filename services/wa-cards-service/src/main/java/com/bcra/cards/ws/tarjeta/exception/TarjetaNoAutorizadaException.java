package com.bcra.cards.ws.tarjeta.exception;

import com.bcra.cards.ws.exception.NotFoundException;

/** Oculta la existencia de una tarjeta que no pertenece al titular. */
public class TarjetaNoAutorizadaException extends NotFoundException {

    public TarjetaNoAutorizadaException() {
        super("No se encontró la tarjeta");
    }
}
