package com.bcra.insurance.ws.exception;

/** Credencial de sesión ausente, inválida, expirada o revocada. */
public class UnauthorizedException extends RuntimeException {

    public UnauthorizedException(String message) {
        super(message);
    }
}
