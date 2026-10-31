package com.bcra.channel.ws.vinculo.exception;

import com.bcra.channel.ws.exception.ConflictException;

/** El vínculo no admite la operación en su estado actual (ya bloqueado, desvinculado u otro). */
public class VinculoEstadoConflictoException extends ConflictException {

    public VinculoEstadoConflictoException() {
        super("El vínculo no admite la operación en su estado actual");
    }
}
