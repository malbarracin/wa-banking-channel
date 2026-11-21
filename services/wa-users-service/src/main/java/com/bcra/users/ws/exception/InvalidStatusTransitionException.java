package com.bcra.users.ws.exception;

public class InvalidStatusTransitionException extends ConflictException {
    public InvalidStatusTransitionException() {
        super("No se permite el cambio de estado solicitado");
    }
}
