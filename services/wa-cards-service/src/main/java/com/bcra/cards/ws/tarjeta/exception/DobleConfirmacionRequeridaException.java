package com.bcra.cards.ws.tarjeta.exception;

import com.bcra.cards.ws.exception.ConflictException;

/** Advierte la irreversibilidad antes de solicitar las dos confirmaciones. */
public class DobleConfirmacionRequeridaException extends ConflictException {

    public DobleConfirmacionRequeridaException() {
        super("El bloqueo definitivo es irreversible por WhatsApp. "
                + "Para continuar, confirme dos veces con CONFIRMAR e indique ROBO o EXTRAVIO.");
    }
}
