package com.bcra.users.ws.exception;

public class UserNotFoundException extends NotFoundException {
    public UserNotFoundException() {
        super("No se encontró el usuario");
    }
}
