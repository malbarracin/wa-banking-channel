package com.bcra.users.ws.support;

import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

public final class GatewayJwtTokens {
    public static final String ISSUER = "https://gateway.test/iam";
    private static final RSAKey KEY = generateKey();

    private GatewayJwtTokens() {
    }

    public static RSAPublicKey publicKey() throws JOSEException {
        return KEY.toRSAPublicKey();
    }

    public static String bearer(String subject, String role) {
        Instant issuedAt = Instant.now().minusSeconds(5);
        return "Bearer " + token(subject, role, issuedAt, issuedAt.plusSeconds(3600));
    }

    public static String bearerExpired(String subject, String role) {
        Instant expiresAt = Instant.now().minusSeconds(60);
        return "Bearer " + token(subject, role, expiresAt.minusSeconds(300), expiresAt);
    }

    public static String token(String subject, String role, Instant issuedAt, Instant expiresAt) {
        try {
            var claims = new JWTClaimsSet.Builder()
                    .issuer(ISSUER)
                    .audience("wa-users-service")
                    .subject(subject)
                    .issueTime(Date.from(issuedAt))
                    .expirationTime(Date.from(expiresAt))
                    .jwtID(UUID.randomUUID().toString())
                    .claim("role", role)
                    .build();
            var jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(KEY.getKeyID()).build(), claims);
            jwt.sign(new RSASSASigner(KEY.toPrivateKey()));
            return jwt.serialize();
        } catch (JOSEException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static RSAKey generateKey() {
        try {
            return new RSAKeyGenerator(2048).keyID("test").generate();
        } catch (JOSEException exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }
}
