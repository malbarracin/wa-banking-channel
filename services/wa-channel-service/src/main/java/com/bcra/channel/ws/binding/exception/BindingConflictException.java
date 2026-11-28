package com.bcra.channel.ws.binding.exception;

import com.bcra.channel.ws.exception.ConflictException;

/**
 * Conflicto de estado del vínculo o transición no permitida.
 */
public class BindingConflictException extends ConflictException {

    public BindingConflictException(String message) {
        super(message);
    }
}
