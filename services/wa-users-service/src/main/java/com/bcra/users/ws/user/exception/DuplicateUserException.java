package com.bcra.users.ws.user.exception;

import com.bcra.users.ws.exception.ConflictException;
import lombok.Getter;

@Getter
public class DuplicateUserException extends ConflictException {
    private final String field;

    public DuplicateUserException(String field) {
        super("DUPLICADO");
        this.field = field;
    }
}
