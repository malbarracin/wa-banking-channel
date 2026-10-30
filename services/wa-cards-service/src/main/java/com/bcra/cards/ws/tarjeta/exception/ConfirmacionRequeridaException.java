package com.bcra.cards.ws.tarjeta.exception;

import com.bcra.cards.ws.exception.ConflictException;

/** Impide cambiar el estado sin la confirmación requerida. */
public class ConfirmacionRequeridaException extends ConflictException {

    public ConfirmacionRequeridaException() {
        super("Se requiere confirmación explícita para continuar");
    }
}
