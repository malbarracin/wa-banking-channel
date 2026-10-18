package com.bank.users.user.exception;

import com.bank.users.exception.NotFoundException;

/** No hay usuario para el identificador solicitado; no expone datos personales. */
public class UserNotFoundException extends NotFoundException {

    public UserNotFoundException() {
        super("No existe el usuario solicitado");
    }
}
