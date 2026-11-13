package com.bcra.ws.users.exception;

import java.util.List;

import lombok.Getter;

/** Base para conflictos de dominio con detalles seguros. */
@Getter
public abstract class ConflictException extends RuntimeException {

    private final List<FieldError> details;

    protected ConflictException(String message, List<FieldError> details) {
        super(message);
        this.details = List.copyOf(details);
    }
}
