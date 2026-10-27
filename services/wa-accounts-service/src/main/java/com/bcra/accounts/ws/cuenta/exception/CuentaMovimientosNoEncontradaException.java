package com.bcra.accounts.ws.cuenta.exception;

import com.bcra.accounts.ws.exception.NotFoundException;

/** No revela si una cuenta pertenece a otro titular o no existe. */
public class CuentaMovimientosNoEncontradaException extends NotFoundException {

    public CuentaMovimientosNoEncontradaException() {
        super("Cuenta no encontrada");
    }
}
