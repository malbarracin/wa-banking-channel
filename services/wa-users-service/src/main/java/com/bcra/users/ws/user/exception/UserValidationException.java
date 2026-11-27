package com.bcra.users.ws.user.exception;

import java.util.List;
import com.bcra.users.ws.exception.FieldError;
import lombok.Getter;

@Getter
public class UserValidationException extends RuntimeException {
    private final List<FieldError> details;

    public UserValidationException(List<FieldError> details) {
        this.details = List.copyOf(details);
    }
}
