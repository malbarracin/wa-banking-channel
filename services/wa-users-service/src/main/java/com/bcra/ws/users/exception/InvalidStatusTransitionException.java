package com.bcra.ws.users.exception;

import java.util.List;

import com.bcra.ws.users.domain.UserStatus;

/** Transición de estado rechazada por el dominio. */
public class InvalidStatusTransitionException extends ConflictException {

    public InvalidStatusTransitionException(UserStatus from, UserStatus to) {
        super("Invalid status transition from " + from + " to " + to,
                List.of(new FieldError("status", "Status transition is not allowed")));
    }
}
