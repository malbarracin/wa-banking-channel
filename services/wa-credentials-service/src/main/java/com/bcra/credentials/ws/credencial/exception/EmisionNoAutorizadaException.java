package com.bcra.credentials.ws.credencial.exception;

/** La confirmación del canal o el estado bancario no permite emitir. */
public class EmisionNoAutorizadaException extends RuntimeException {

    public EmisionNoAutorizadaException() {
        super("La emisión requiere identidad verificada, vínculo activo y cliente activo");
    }
}
