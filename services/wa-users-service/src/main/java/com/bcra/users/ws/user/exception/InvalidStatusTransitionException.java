package com.bcra.users.ws.user.exception;

/** Transición no soportada; el contrato de este caso de uso exige HTTP 400. */
public class InvalidStatusTransitionException extends IllegalArgumentException {

    public InvalidStatusTransitionException() {
        super("El cambio de estado no está soportado");
    }
}
