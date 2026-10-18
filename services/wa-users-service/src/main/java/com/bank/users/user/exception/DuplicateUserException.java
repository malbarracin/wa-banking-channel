package com.bank.users.user.exception;

import com.bank.users.exception.ConflictException;

/** El documento ya está reservado, independientemente del estado del usuario. */
public class DuplicateUserException extends ConflictException {

    public DuplicateUserException() {
        super("Ya existe un usuario con ese documento");
    }
}
