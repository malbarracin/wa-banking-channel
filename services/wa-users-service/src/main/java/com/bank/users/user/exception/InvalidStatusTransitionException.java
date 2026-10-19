package com.bank.users.user.exception;

import com.bank.users.exception.ConflictException;

/** Rechaza transiciones fuera de la política de estados operativos. */
public class InvalidStatusTransitionException extends ConflictException {

    public InvalidStatusTransitionException() {
        super("La transición de estado solicitada no está permitida");
    }
}
