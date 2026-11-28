package com.bcra.users.ws.user.exception;

import com.bcra.users.ws.exception.NotFoundException;

public class UserNotFoundException extends NotFoundException {
    public UserNotFoundException() {
        super("No existe el usuario solicitado");
    }
}
