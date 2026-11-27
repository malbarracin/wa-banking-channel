package com.bcra.users.ws.user.exception;

import com.bcra.users.ws.exception.ConflictException;
import com.bcra.users.ws.user.entity.UserStatus;
import lombok.Getter;

/** Transición de estado no permitida por la política del banco. */
@Getter
public class InvalidStatusTransitionException extends ConflictException {
    private final UserStatus previousStatus;
    private final UserStatus newStatus;

    public InvalidStatusTransitionException(UserStatus previousStatus, UserStatus newStatus) {
        super("Transición de estado no permitida");
        this.previousStatus = previousStatus;
        this.newStatus = newStatus;
    }
}
