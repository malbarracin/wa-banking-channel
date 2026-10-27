package com.bcra.accounts.ws.cuenta.client;

/** No se pudo obtener una validación fiable de H2; impide autorizar la operación. */
public class CredencialesServiceUnavailableException extends RuntimeException {

    public CredencialesServiceUnavailableException() {
        super("No se pudo validar la credencial con el servicio de credenciales");
    }

    public CredencialesServiceUnavailableException(Throwable cause) {
        super("No se pudo validar la credencial con el servicio de credenciales", cause);
    }
}
