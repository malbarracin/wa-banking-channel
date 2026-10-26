package com.bcra.users.ws.user.exception;

import com.bcra.users.ws.exception.ConflictException;

/** Conflicto por documento reservado por cualquier usuario, independientemente de su estado. */
public class DuplicateUserException extends ConflictException {

    public DuplicateUserException() {
        super("Ya existe un usuario con ese documento (duplicado)");
    }
}
