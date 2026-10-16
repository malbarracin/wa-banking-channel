package com.bank.users.user.exception;

import com.bank.users.exception.ConflictException;

/** El estado solicitado no es alcanzable desde el estado actual del usuario. */
public class InvalidUserStatusTransitionException extends ConflictException {

    public InvalidUserStatusTransitionException() {
        super("La transición de estado solicitada no está permitida");
    }
}
