package com.bcra.orchestrator.messaging.consumer;

/** Señala un evento que no cumple el contrato y no debe reintentarse. */
public class InvalidEventException extends RuntimeException {
    public InvalidEventException() {
        super("Event validation failed");
    }
}
