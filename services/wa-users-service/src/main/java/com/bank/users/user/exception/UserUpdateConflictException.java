package com.bank.users.user.exception;

import com.bank.users.exception.ConflictException;

/** La baja lógica impide modificar datos del usuario. */
public class UserUpdateConflictException extends ConflictException {

    public UserUpdateConflictException() {
        super("No se pueden actualizar datos de un usuario dado de baja lógica");
    }
}
