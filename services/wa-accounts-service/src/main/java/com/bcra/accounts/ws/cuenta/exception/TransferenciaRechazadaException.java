package com.bcra.accounts.ws.cuenta.exception;

import com.bcra.accounts.ws.exception.ConflictException;

/** Rechazo de una preparación por las reglas operativas del banco. */
public class TransferenciaRechazadaException extends ConflictException {

    public TransferenciaRechazadaException(String motivo) {
        super(motivo);
    }
}

