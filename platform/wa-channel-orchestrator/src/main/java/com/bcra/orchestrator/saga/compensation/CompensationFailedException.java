package com.bcra.orchestrator.saga.compensation;

/** Adapter reports an unconfirmed remote effect without exposing sensitive details. */
public class CompensationFailedException extends RuntimeException {
    public CompensationFailedException(Throwable cause) {
        super("Saga compensation requires support", cause);
    }
}
