package com.bcra.users.ws.support;

import java.time.Instant;

import com.nimbusds.jose.JOSEException;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

@TestConfiguration(proxyBeanMethods = false)
public class GatewayJwtTestConfig {
    @Bean
    @Primary
    JwtDecoder jwtDecoder() throws JOSEException {
        var decoder = NimbusJwtDecoder.withPublicKey(GatewayJwtTokens.publicKey()).build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(GatewayJwtTokens.ISSUER),
                jwt -> {
                    boolean valid = jwt.getAudience() != null && jwt.getAudience().contains("wa-users-service")
                            && jwt.getExpiresAt() != null && jwt.getIssuedAt() != null
                            && !jwt.getIssuedAt().isAfter(Instant.now())
                            && jwt.getId() != null && !jwt.getId().isBlank();
                    return valid ? OAuth2TokenValidatorResult.success()
                            : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token"));
                }));
        return decoder;
    }
}
