package com.bcra.orchestrator.agent.client;

/** Safe failure for saga contingency/support handling; never contains HTTP bodies or credentials. */
public class H9TurnException extends RuntimeException {

    public enum Reason { TIMEOUT, UNAVAILABLE, REJECTED, INVALID_RESPONSE, NOT_CONFIGURED }

    private final Reason reason;

    public H9TurnException(Reason reason) {
        super("H9 turn failed: " + reason);
        this.reason = reason;
    }

    public Reason getReason() {
        return reason;
    }

    public boolean isRetryable() {
        return reason == Reason.TIMEOUT || reason == Reason.UNAVAILABLE;
    }
}
