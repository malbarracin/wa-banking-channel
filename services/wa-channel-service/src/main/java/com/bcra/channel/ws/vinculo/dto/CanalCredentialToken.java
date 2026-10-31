package com.bcra.channel.ws.vinculo.dto;

/** Credencial opaca emitida por H2; nunca debe exponerse en logs. */
public record CanalCredentialToken(String valor) {
    @Override
    public String toString() {
        return "CanalCredentialToken[REDACTED]";
    }
}
