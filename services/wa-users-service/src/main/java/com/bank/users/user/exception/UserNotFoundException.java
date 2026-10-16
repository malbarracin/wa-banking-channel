package com.bank.users.user.exception;

import com.bank.users.exception.NotFoundException;

/** Usuario inexistente para el identificador solicitado. */
public class UserNotFoundException extends NotFoundException {

    public UserNotFoundException() {
        super("El usuario solicitado no existe");
    }
}
