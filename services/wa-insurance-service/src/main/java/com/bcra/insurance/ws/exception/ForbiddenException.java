package com.bcra.insurance.ws.exception;

/** Acceso sin una relación autorizada con el recurso. */
public class ForbiddenException extends RuntimeException {

    public ForbiddenException(String message) {
        super(message);
    }
}
