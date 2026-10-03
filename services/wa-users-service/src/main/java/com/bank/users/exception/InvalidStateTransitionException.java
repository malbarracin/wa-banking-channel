package com.bank.users.exception;

/**
 * Transición de estado no permitida. Se traduce a HTTP 400 {@code BAD_REQUEST} (arquitectura + OQ-5).
 *
 * <p>Usar {@link #terminalState()} cuando el origen es la baja lógica ({@code INACTIVE}).</p>
 */
public class InvalidStateTransitionException extends RuntimeException {

    /** Mensaje obligatorio cuando el usuario está en baja lógica. */
    public static final String TERMINAL_STATE_MESSAGE = "la baja lógica es un estado terminal";

    private static final String NOT_ALLOWED_MESSAGE = "Transición de estado no permitida: %s -> %s";

    /**
     * @param message mensaje sin datos sensibles
     */
    public InvalidStateTransitionException(String message) {
        super(message);
    }

    /**
     * @return excepción para cualquier transición desde la baja lógica
     */
    public static InvalidStateTransitionException terminalState() {
        return new InvalidStateTransitionException(TERMINAL_STATE_MESSAGE);
    }

    /**
     * @param from estado actual
     * @param to   estado solicitado
     * @return excepción para una transición no contemplada
     */
    public static InvalidStateTransitionException notAllowed(Enum<?> from, Enum<?> to) {
        return new InvalidStateTransitionException(String.format(NOT_ALLOWED_MESSAGE, from, to));
    }
}
