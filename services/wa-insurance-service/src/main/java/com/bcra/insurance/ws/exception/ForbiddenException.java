package com.bcra.insurance.ws.exception;

/** El cliente autenticado no tiene autorización para acceder al recurso. */
public class ForbiddenException extends RuntimeException {

    public ForbiddenException(String message) {
        super(message);
    }
}
