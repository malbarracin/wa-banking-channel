package com.bcra.accounts.ws.cuenta.exception;

import com.bcra.accounts.ws.exception.NotFoundException;

/** Oculta transferencias inexistentes o pertenecientes a otros titulares. */
public class TransferenciaNoEncontradaException extends NotFoundException {

    public TransferenciaNoEncontradaException() {
        super("Transferencia no encontrada");
    }
}
