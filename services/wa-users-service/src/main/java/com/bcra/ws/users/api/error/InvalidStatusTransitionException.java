package com.bcra.ws.users.api.error;

import com.bcra.ws.users.domain.UserStatus;

/**
 * Transición de estado no permitida por la política U4.
 *
 * @since 2026-10-19
 */
public class InvalidStatusTransitionException extends IllegalArgumentException {

    public InvalidStatusTransitionException(UserStatus from, UserStatus to) {
        super("Invalid status transition from " + from + " to " + to);
    }
}
