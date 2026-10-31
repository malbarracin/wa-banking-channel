package com.bcra.credentials.ws.config;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.bcra.credentials.ws.exception.CallerForbiddenException;
import com.bcra.credentials.ws.exception.UnauthorizedException;

/** Autentica servicios con secretos independientes de las credenciales de clientes. */
@Component
public class CallerAuthorization {

    private final String h3Token;
    private final String bankToken;

    public CallerAuthorization(@Value("${CREDENTIAL_H3_SERVICE_TOKEN:}") String h3Token,
            @Value("${CREDENTIAL_BANK_SERVICE_TOKEN:}") String bankToken) {
        if (!h3Token.isBlank() && h3Token.equals(bankToken)) {
            throw new IllegalArgumentException("Los secretos de H3 y banco deben ser diferentes");
        }
        this.h3Token = h3Token;
        this.bankToken = bankToken;
    }

    /** Exige H3 para emisión y revocación individual; banco para revocación masiva. */
    public void requireActor(String authorization, boolean bankRequired) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            throw new UnauthorizedException("Se requiere autenticación de servicio válida");
        }
        String presented = authorization.substring(7);
        boolean h3 = matches(h3Token, presented);
        boolean bank = matches(bankToken, presented);
        if (!h3 && !bank) {
            throw new UnauthorizedException("Se requiere autenticación de servicio válida");
        }
        if (bankRequired ? !bank : !h3) {
            throw new CallerForbiddenException();
        }
    }

    private boolean matches(String expected, String presented) {
        return !expected.isBlank() && MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                presented.getBytes(StandardCharsets.UTF_8));
    }
}
