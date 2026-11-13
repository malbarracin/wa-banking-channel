package com.bcra.ws.users.exception;

/** Usuario inexistente; no expone el identificador consultado. */
public class UserNotFoundException extends NotFoundException {

    public UserNotFoundException(String id) {
        super("User not found");
    }

    public static UserNotFoundException byDocument(String documentType, String documentNumber) {
        return new UserNotFoundException("");
    }
}
