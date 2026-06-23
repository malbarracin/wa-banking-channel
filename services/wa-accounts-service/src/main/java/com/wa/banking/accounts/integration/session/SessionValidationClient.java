package com.wa.banking.accounts.integration.session;

import com.wa.banking.accounts.config.IntegrationProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Cliente para validación de credenciales de sesión contra H2 (stub configurable).
 *
 * @author licius-it
 * @since 2026-06-23
 * @contact marceloalejandro.albarracin@gmail.com
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SessionValidationClient {

    private static final String STUB_BANK_USER_ID = "user-demo-001";

    private static final String STUB_CHANNEL_LINK_ID = "link-demo-001";

    private final IntegrationProperties integrationProperties;

    private final RestClient sessionRestClient;

    /**
     * Valida una credencial de sesión presentada por el consumidor.
     *
     * @param credentialId identificador de la credencial
     * @param token        token opaco
     * @return resultado de validación
     */
    public ValidateCredentialResponse validate(String credentialId, String token) {
        if (integrationProperties.getSession().isStubEnabled()) {
            log.debug("H2 stub: validating credential {}", credentialId);
            return ValidateCredentialResponse.builder()
                    .valid(true)
                    .bankUserId(STUB_BANK_USER_ID)
                    .channelLinkId(STUB_CHANNEL_LINK_ID)
                    .expiresAt(Instant.now().plus(24, ChronoUnit.HOURS))
                    .build();
        }

        return sessionRestClient.post()
                .uri("/api/v1/sessions/credentials/validate")
                .body(ValidateCredentialRequest.builder()
                        .credentialId(credentialId)
                        .token(token)
                        .build())
                .retrieve()
                .body(ValidateCredentialResponse.class);
    }
}
