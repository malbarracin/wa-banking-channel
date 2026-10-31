package com.bcra.credentials.ws.config;

import java.nio.charset.StandardCharsets;

import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import com.nimbusds.jose.jwk.source.ImmutableSecret;

/** Firma HS256 con una clave externa de al menos 256 bits, sin valor por defecto. */
@Configuration
public class JwtConfig {

    private static final int MIN_SECRET_BYTES = 32;

    @Bean
    JwtEncoder jwtEncoder(@Value("${credencial.jwt.secret}") String secret) {
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < MIN_SECRET_BYTES) {
            throw new IllegalArgumentException("La clave JWT debe tener al menos 32 bytes");
        }
        return new NimbusJwtEncoder(new ImmutableSecret<>(new SecretKeySpec(bytes, "HmacSHA256")));
    }
}
