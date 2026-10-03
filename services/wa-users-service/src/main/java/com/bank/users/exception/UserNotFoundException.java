package com.bank.users.exception;

/**
 * El usuario solicitado no existe. Se traduce a HTTP 404 {@code NOT_FOUND}.
 *
 * <p>El mensaje no incluye el documento consultado (dato personal).</p>
 */
public class UserNotFoundException extends RuntimeException {

    private static final String BY_ID_MESSAGE = "No existe un usuario con id %d";
    private static final String BY_DOCUMENT_MESSAGE = "No existe un usuario con el documento informado";

    /**
     * @param message mensaje sin datos sensibles
     */
    public UserNotFoundException(String message) {
        super(message);
    }

    /**
     * @param id identificador interno consultado
     * @return excepción para búsqueda por id
     */
    public static UserNotFoundException byId(Long id) {
        return new UserNotFoundException(String.format(BY_ID_MESSAGE, id));
    }

    /**
     * @return excepción para búsqueda por documento (sin exponer el documento)
     */
    public static UserNotFoundException byDocument() {
        return new UserNotFoundException(BY_DOCUMENT_MESSAGE);
    }
}
