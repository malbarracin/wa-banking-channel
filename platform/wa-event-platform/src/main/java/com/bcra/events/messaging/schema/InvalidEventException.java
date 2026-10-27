package com.bcra.events.messaging.schema;

public class InvalidEventException extends RuntimeException {
    public InvalidEventException() {
        super("SCHEMA_VALIDATION_FAILED");
    }
}
