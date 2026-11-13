package com.bcra.ws.users.exception;

import java.util.List;

/** Conflicto por email duplicado. */
public class DuplicateEmailException extends ConflictException {

    public DuplicateEmailException() {
        super("A user with the same email already exists",
                List.of(new FieldError("email", "Email already registered")));
    }
}
