package com.bank.users.exception;

/**
 * Ya existe un usuario con el mismo tipo y número de documento. Se traduce a HTTP 409 {@code CONFLICT}.
 *
 * <p>El mensaje no repite el documento (dato personal).</p>
 */
public class UserAlreadyExistsException extends RuntimeException {

    /** Mensaje por defecto del duplicado de documento. */
    public static final String DEFAULT_MESSAGE = "Ya existe un usuario registrado con el documento informado";

    /**
     * Crea la excepción con el mensaje por defecto.
     */
    public UserAlreadyExistsException() {
        super(DEFAULT_MESSAGE);
    }
}
