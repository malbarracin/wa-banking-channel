package com.bcra.cards.ws.tarjeta.exception;

/** Rechaza un bloqueo temporal sin identificador coherente y confirmación explícita. */
public class SolicitudBloqueoInvalidaException extends IllegalArgumentException {

    public SolicitudBloqueoInvalidaException() {
        super("Se requiere el identificador y la confirmación CONFIRMAR");
    }
}
