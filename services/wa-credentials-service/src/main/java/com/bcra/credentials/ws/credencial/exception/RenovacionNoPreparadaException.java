package com.bcra.credentials.ws.credencial.exception;

import com.bcra.credentials.ws.exception.NotFoundException;

/** La credencial sigue vigente pero el scheduler todavía no preparó un reemplazo. */
public class RenovacionNoPreparadaException extends NotFoundException {

    public RenovacionNoPreparadaException() {
        super("Todavía no hay una renovación preparada");
    }
}
