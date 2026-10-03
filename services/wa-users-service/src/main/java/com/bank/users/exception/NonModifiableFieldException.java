package com.bank.users.exception;

import java.util.List;

import lombok.Getter;

/**
 * La solicitud intenta modificar campos que este servicio no permite actualizar.
 * Se traduce a HTTP 400 {@code BAD_REQUEST} con un detalle por campo.
 */
@Getter
public class NonModifiableFieldException extends RuntimeException {

    /** Mensaje para campos prohibidos en la actualización. */
    public static final String MESSAGE = "campo no modificable por este servicio";

    /** Campos rechazados (inmutable, nunca {@code null}). */
    private final List<String> fields;

    /**
     * @param fields nombres de los campos rechazados
     */
    public NonModifiableFieldException(List<String> fields) {
        super(MESSAGE);
        this.fields = fields == null ? List.of() : List.copyOf(fields);
    }
}
