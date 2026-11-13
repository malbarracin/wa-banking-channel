package com.ingenia.wa.users.api.error;

/**
 * Usuario no encontrado por id interno o por documento.
 *
 * @author Ingenia
 * @since 2026-10-19
 * Contacto: marcelo.albarracin@ingenia.la
 */
public class UserNotFoundException extends ResourceNotFoundException {

    public UserNotFoundException(String id) {
        super("User not found: " + id);
    }

    public static UserNotFoundException byDocument(String documentType, String documentNumber) {
        return new UserNotFoundException("document=" + documentType + "/" + documentNumber);
    }
}
