package com.ingenia.wa.users.api.error;

import com.ingenia.wa.users.domain.UserStatus;

/**
 * Transición de estado no permitida por la política U4.
 *
 * @author Ingenia
 * @since 2026-10-19
 * Contacto: marcelo.albarracin@ingenia.la
 */
public class InvalidStatusTransitionException extends IllegalArgumentException {

    public InvalidStatusTransitionException(UserStatus from, UserStatus to) {
        super("Invalid status transition from " + from + " to " + to);
    }
}
