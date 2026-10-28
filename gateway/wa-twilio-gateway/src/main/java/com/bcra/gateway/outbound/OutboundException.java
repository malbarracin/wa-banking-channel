package com.bcra.gateway.outbound;

public class OutboundException extends RuntimeException {
    public enum Kind { INVALID, PERMANENT, TRANSIENT, AMBIGUOUS }

    private final Kind kind;
    private final String code;

    public OutboundException(Kind kind, String code) {
        super(code);
        this.kind = kind;
        this.code = code;
    }

    public Kind kind() {
        return kind;
    }

    public String code() {
        return code;
    }
}
