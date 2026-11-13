package com.bcra.ws.users.api.error;

/**
 * Usuario no encontrado por id interno o por documento.
 *
 * @since 2026-10-19
 */
public class UserNotFoundException extends ResourceNotFoundException {

    public UserNotFoundException(String id) {
        super("User not found: " + id);
    }

    public static UserNotFoundException byDocument(String documentType, String documentNumber) {
        return new UserNotFoundException("document=" + documentType + "/" + documentNumber);
    }
}
