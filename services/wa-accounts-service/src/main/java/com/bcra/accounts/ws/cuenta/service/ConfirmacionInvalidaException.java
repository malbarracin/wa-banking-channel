package com.bcra.accounts.ws.cuenta.service;

import com.bcra.accounts.ws.cuenta.exception.TransferenciaRechazadaException;

/** Permite persistir la interrupción de la secuencia sin modificar fondos. */
public class ConfirmacionInvalidaException extends TransferenciaRechazadaException {

    public ConfirmacionInvalidaException() {
        super("Se requiere confirmación explícita CONFIRMAR");
    }
}
