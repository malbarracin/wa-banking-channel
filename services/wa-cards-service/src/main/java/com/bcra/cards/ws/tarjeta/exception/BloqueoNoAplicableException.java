package com.bcra.cards.ws.tarjeta.exception;

import com.bcra.cards.ws.exception.ConflictException;

/** Estado incompatible con el bloqueo solicitado. */
public class BloqueoNoAplicableException extends ConflictException {

    public BloqueoNoAplicableException() {
        super("El estado de la tarjeta no permite aplicar el bloqueo");
    }
}
