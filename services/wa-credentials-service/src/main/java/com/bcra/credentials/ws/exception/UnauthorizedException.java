package com.bcra.credentials.ws.exception;

/** Rechazo de autenticación sin detalles sobre la credencial presentada. */
public class UnauthorizedException extends RuntimeException {

    public UnauthorizedException() {
        super("La credencial no es válida; se requiere verificar la identidad nuevamente");
    }
}
