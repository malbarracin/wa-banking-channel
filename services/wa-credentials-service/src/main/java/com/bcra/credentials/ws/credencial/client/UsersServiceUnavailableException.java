package com.bcra.credentials.ws.credencial.client;

/** Falla controlada de H1; el consumidor decide si su caso permite continuar. */
public class UsersServiceUnavailableException extends RuntimeException {

    public UsersServiceUnavailableException() {
        super("No se pudo consultar el estado del cliente");
    }
}
