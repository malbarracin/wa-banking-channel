package com.bcra.cards.ws.tarjeta.exception;

import com.bcra.cards.ws.exception.ConflictException;

/** Rechaza la reversión por chat de un bloqueo definitivo. */
public class BloqueoDefinitivoIrreversibleException extends ConflictException {

    public BloqueoDefinitivoIrreversibleException() {
        super("El bloqueo definitivo no se puede revertir por chat. Contactá los canales formales del banco");
    }
}
