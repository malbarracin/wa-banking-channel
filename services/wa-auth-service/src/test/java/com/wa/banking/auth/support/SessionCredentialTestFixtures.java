package com.wa.banking.auth.support;

import com.wa.banking.auth.api.v1.dto.IssueCredentialRequest;
import com.wa.banking.auth.api.v1.dto.RevokeByUserRequest;
import com.wa.banking.auth.api.v1.dto.ValidateCredentialRequest;
import com.wa.banking.auth.entity.CredentialStatus;
import com.wa.banking.auth.entity.RevokeReason;
import com.wa.banking.auth.entity.SessionCredentialEntity;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Datos de prueba reutilizables para tests de credenciales de sesión.
 */
public final class SessionCredentialTestFixtures {

    public static final String CHANNEL_LINK_ID = "link-abc123";
    public static final String BANK_USER_ID = "user-xyz789";
    public static final String PHONE = "+541112345678";
    public static final String CREDENTIAL_ID = "cred-test-001";
    public static final String TOKEN = "sess_test_token_abc";
    public static final String TOKEN_HASH = "hashed-token-value";

    private SessionCredentialTestFixtures() {
    }

    public static IssueCredentialRequest issueRequest() {
        return IssueCredentialRequest.builder()
                .channelLinkId(CHANNEL_LINK_ID)
                .bankUserId(BANK_USER_ID)
                .phoneNumber(PHONE)
                .identityVerified(true)
                .build();
    }

    public static IssueCredentialRequest issueRequestUnverified() {
        return IssueCredentialRequest.builder()
                .channelLinkId(CHANNEL_LINK_ID)
                .bankUserId(BANK_USER_ID)
                .phoneNumber(PHONE)
                .identityVerified(false)
                .build();
    }

    public static ValidateCredentialRequest validateRequest() {
        return ValidateCredentialRequest.builder()
                .credentialId(CREDENTIAL_ID)
                .token(TOKEN)
                .build();
    }

    public static RevokeByUserRequest revokeByUserRequest() {
        return RevokeByUserRequest.builder()
                .bankUserId(BANK_USER_ID)
                .reason(RevokeReason.POLICY)
                .build();
    }

    public static SessionCredentialEntity activeCredential() {
        return SessionCredentialEntity.builder()
                .id(CREDENTIAL_ID)
                .channelLinkId(CHANNEL_LINK_ID)
                .bankUserId(BANK_USER_ID)
                .phoneNumber(PHONE)
                .status(CredentialStatus.ACTIVE)
                .tokenHash(TOKEN_HASH)
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plus(24, ChronoUnit.HOURS))
                .renewalCount(0)
                .build();
    }

    public static SessionCredentialEntity expiredCredential() {
        SessionCredentialEntity entity = activeCredential();
        entity.setExpiresAt(Instant.now().minus(1, ChronoUnit.HOURS));
        return entity;
    }

    public static SessionCredentialEntity revokedCredential() {
        SessionCredentialEntity entity = activeCredential();
        entity.setStatus(CredentialStatus.REVOKED);
        entity.setRevokedAt(Instant.now());
        entity.setRevokeReason(RevokeReason.POLICY);
        return entity;
    }
}
