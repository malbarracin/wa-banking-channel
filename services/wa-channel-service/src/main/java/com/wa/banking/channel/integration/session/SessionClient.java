package com.wa.banking.channel.integration.session;

import com.wa.banking.channel.config.IntegrationProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * Cliente para emisión y revocación de credenciales de sesión H2 (stub configurable).
 *
 * @author licius-it
 * @since 2026-06-23
 * @contact marceloalejandro.albarracin@gmail.com
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SessionClient {

    private final IntegrationProperties integrationProperties;

    private RestClient restClient;

    private RestClient restClient() {
        if (restClient == null) {
            restClient = RestClient.builder()
                    .baseUrl(integrationProperties.getSession().getBaseUrl())
                    .build();
        }
        return restClient;
    }

    /**
     * Emite una credencial de sesión tras verificación exitosa (RN3).
     *
     * @param linkId     identificador del vínculo
     * @param bankUserId identificador del usuario bancario
     * @return credencial emitida
     */
    public SessionCredentialResponse issueCredential(String linkId, String bankUserId) {
        if (integrationProperties.getSession().isStubEnabled()) {
            String credentialId = "stub-cred-" + UUID.randomUUID();
            log.info("H2 stub: issued credential for link {}", linkId);
            return SessionCredentialResponse.builder()
                    .credentialId(credentialId)
                    .expiresAt(Instant.now().plus(24, ChronoUnit.HOURS))
                    .build();
        }

        return restClient().post()
                .uri("/api/v1/sessions/credentials")
                .body(new IssueCredentialRequest(linkId, bankUserId))
                .retrieve()
                .body(SessionCredentialResponse.class);
    }

    /**
     * Revoca una credencial de sesión (RN4).
     *
     * @param credentialId identificador de la credencial
     */
    public void revokeCredential(String credentialId) {
        if (credentialId == null || credentialId.isBlank()) {
            return;
        }
        if (integrationProperties.getSession().isStubEnabled()) {
            log.info("H2 stub: revoked credential {}", credentialId);
            return;
        }

        restClient().delete()
                .uri("/api/v1/sessions/credentials/{id}", credentialId)
                .retrieve()
                .toBodilessEntity();
    }

    private record IssueCredentialRequest(String linkId, String bankUserId) {
    }
}
