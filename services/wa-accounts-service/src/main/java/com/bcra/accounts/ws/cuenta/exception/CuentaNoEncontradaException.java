package com.bcra.accounts.ws.cuenta.exception;

import com.bcra.accounts.ws.exception.NotFoundException;

/** No distingue cuentas inexistentes de cuentas de otros titulares. */
public class CuentaNoEncontradaException extends NotFoundException {

    public CuentaNoEncontradaException() {
        super("La cuenta no existe");
    }
}
