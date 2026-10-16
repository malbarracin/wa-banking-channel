package com.bank.users.user.exception;

import com.bank.users.exception.ConflictException;

/** Rechazo del alta por el par documental único, sin incluir PII. */
public class DuplicateUserException extends ConflictException {

    public DuplicateUserException() {
        super("Ya existe un usuario con ese documento");
    }

    public DuplicateUserException(Throwable cause) {
        this();
        initCause(cause);
    }
}
