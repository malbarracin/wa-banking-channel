package com.bcra.credentials.ws.exception;

/** El servicio autenticado carece de permiso para la operación solicitada. */
public class CallerForbiddenException extends RuntimeException {

    public CallerForbiddenException() {
        super("El servicio llamador no tiene permiso para esta operación");
    }
}
