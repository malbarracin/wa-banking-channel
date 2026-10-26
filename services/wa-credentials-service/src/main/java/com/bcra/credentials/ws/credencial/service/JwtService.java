package com.bcra.credentials.ws.credencial.service;

import java.nio.charset.StandardCharsets;

import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Service;

import com.bcra.credentials.ws.exception.UnauthorizedException;

/** Verifica firma HS256 con una clave externa de al menos 256 bits. */
@Service
public class JwtService {

    private static final int MIN_SECRET_BYTES = 32;
    private static final int MAX_TOKEN_LENGTH = 8192;

    private final String secret;

    public JwtService(@Value("${credencial.jwt.secret:}") String secret) {
        this.secret = secret;
    }

    /** @return claims verificados; la vigencia y correspondencia se comprueban en ValidacionService. */
    public Jwt verificar(String token) {
        byte[] clave = secret.getBytes(StandardCharsets.UTF_8);
        if (clave.length < MIN_SECRET_BYTES) {
            throw new IllegalStateException("Configurar una clave JWT de al menos 256 bits");
        }
        if (token == null || token.isBlank() || token.length() > MAX_TOKEN_LENGTH) {
            throw new UnauthorizedException();
        }
        try {
            return NimbusJwtDecoder.withSecretKey(new SecretKeySpec(clave, "HmacSHA256"))
                    .macAlgorithm(MacAlgorithm.HS256).build().decode(token);
        } catch (JwtException | IllegalArgumentException exception) {
            throw new UnauthorizedException();
        }
    }
}
