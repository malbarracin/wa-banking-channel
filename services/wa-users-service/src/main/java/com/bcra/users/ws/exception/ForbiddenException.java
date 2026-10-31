package com.bcra.users.ws.exception;

/** Acceso a datos personales sin autorización del servidor. */
public class ForbiddenException extends RuntimeException {

    public ForbiddenException() {
        super("Acceso no autorizado a la consulta completa");
    }
}
