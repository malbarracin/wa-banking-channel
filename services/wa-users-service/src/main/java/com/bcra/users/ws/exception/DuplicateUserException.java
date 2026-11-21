package com.bcra.users.ws.exception;

public class DuplicateUserException extends ConflictException {
    public DuplicateUserException() {
        super("El usuario ya existe");
    }
}
