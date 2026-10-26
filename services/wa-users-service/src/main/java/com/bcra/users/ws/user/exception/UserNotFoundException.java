package com.bcra.users.ws.user.exception;

import com.bcra.users.ws.exception.NotFoundException;

/** Ausencia de un usuario sin incluir documentos ni otros datos personales en el error. */
public class UserNotFoundException extends NotFoundException {

    public UserNotFoundException() {
        super("Usuario no encontrado");
    }
}
