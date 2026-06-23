package com.wa.banking.auth.service;

import com.wa.banking.auth.api.v1.dto.AuditEntryResponse;
import com.wa.banking.auth.api.v1.dto.CredentialStatusResponse;
import com.wa.banking.auth.api.v1.dto.IssueCredentialRequest;
import com.wa.banking.auth.api.v1.dto.IssueCredentialResponse;
import com.wa.banking.auth.api.v1.dto.RenewCredentialResponse;
import com.wa.banking.auth.api.v1.dto.RevokeByUserRequest;
import com.wa.banking.auth.api.v1.dto.RevokeCredentialRequest;
import com.wa.banking.auth.api.v1.dto.ValidateCredentialRequest;
import com.wa.banking.auth.api.v1.dto.ValidateCredentialResponse;
import com.wa.banking.auth.api.v1.mapper.SessionCredentialMapper;
import com.wa.banking.auth.entity.AuditAction;
import com.wa.banking.auth.entity.AuditActor;
import com.wa.banking.auth.entity.CredentialStatus;
import com.wa.banking.auth.entity.RevokeReason;
import com.wa.banking.auth.entity.SessionAuditEntryEntity;
import com.wa.banking.auth.entity.SessionCredentialEntity;
import com.wa.banking.auth.exception.CredentialAlreadyRevokedException;
import com.wa.banking.auth.exception.CredentialNotFoundException;
import com.wa.banking.auth.exception.InvalidCredentialException;
import com.wa.banking.auth.exception.VerificationRequiredException;
import com.wa.banking.auth.integration.SessionCredentialProperties;
import com.wa.banking.auth.integration.users.UsersClient;
import com.wa.banking.auth.repository.SessionAuditEntryRepository;
import com.wa.banking.auth.repository.SessionCredentialRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;

/**
 * Implementación de emisión, renovación, revocación y validación de credenciales de sesión.
 *
 * @author licius-it
 * @since 2026-06-23
 * @contact marceloalejandro.albarracin@gmail.com
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SessionCredentialServiceImpl implements SessionCredentialService {

    private final SessionCredentialRepository credentialRepository;
    private final SessionAuditEntryRepository auditEntryRepository;
    private final SessionCredentialMapper credentialMapper;
    private final CredentialTokenService tokenService;
    private final SessionCredentialProperties credentialProperties;
    private final UsersClient usersClient;

    @Override
    @Transactional
    public IssueCredentialResponse issue(IssueCredentialRequest request) {
        if (!isIdentityVerified(request.getIdentityVerified())) {
            throw new VerificationRequiredException();
        }

        usersClient.assertEligibleForSession(request.getBankUserId());

        credentialRepository.findByChannelLinkIdAndStatus(request.getChannelLinkId(), CredentialStatus.ACTIVE)
                .ifPresent(existing -> revokeExistingCredential(existing, RevokeReason.REPLACED, AuditActor.SYSTEM));

        String token = tokenService.generateToken();
        Instant now = Instant.now();
        Instant expiresAt = now.plus(credentialProperties.getTtlHours(), ChronoUnit.HOURS);

        SessionCredentialEntity entity = SessionCredentialEntity.builder()
                .channelLinkId(request.getChannelLinkId())
                .bankUserId(request.getBankUserId())
                .phoneNumber(request.getPhoneNumber())
                .status(CredentialStatus.ACTIVE)
                .tokenHash(tokenService.hashToken(token))
                .issuedAt(now)
                .expiresAt(expiresAt)
                .renewalCount(0)
                .build();

        SessionCredentialEntity saved = credentialRepository.save(entity);
        audit(saved, AuditAction.ISSUED, AuditActor.CHANNEL, null);

        log.info("Session credential issued for channelLinkId={} credentialId={}",
                saved.getChannelLinkId(), saved.getId());

        return credentialMapper.toIssueResponse(saved, token);
    }

    @Override
    @Transactional
    public RenewCredentialResponse renew(String credentialId) {
        SessionCredentialEntity entity = findCredentialOrThrow(credentialId);
        ensureActiveAndNotExpired(entity);

        Instant now = Instant.now();
        entity.setExpiresAt(now.plus(credentialProperties.getTtlHours(), ChronoUnit.HOURS));
        entity.setRenewalCount(entity.getRenewalCount() + 1);

        SessionCredentialEntity saved = credentialRepository.save(entity);
        audit(saved, AuditAction.RENEWED, AuditActor.SYSTEM, null);

        log.info("Session credential renewed credentialId={} renewalCount={}", saved.getId(), saved.getRenewalCount());

        return credentialMapper.toRenewResponse(saved);
    }

    @Override
    @Transactional
    public void revoke(String credentialId, RevokeReason reason, AuditActor actor) {
        SessionCredentialEntity entity = findCredentialOrThrow(credentialId);
        if (entity.getStatus() == CredentialStatus.REVOKED) {
            return;
        }
        applyRevocation(entity, reason, actor);
    }

    @Override
    @Transactional
    public void revokeWithReason(String credentialId, RevokeCredentialRequest request, AuditActor actor) {
        RevokeReason reason = request.getReason() != null ? request.getReason() : RevokeReason.POLICY;
        revoke(credentialId, reason, actor);
    }

    @Override
    @Transactional
    public int revokeByUser(RevokeByUserRequest request) {
        RevokeReason reason = request.getReason() != null ? request.getReason() : RevokeReason.POLICY;
        List<SessionCredentialEntity> activeCredentials =
                credentialRepository.findByBankUserIdAndStatus(request.getBankUserId(), CredentialStatus.ACTIVE);

        activeCredentials.forEach(entity -> applyRevocation(entity, reason, AuditActor.BANK));

        log.info("Revoked {} active credentials for bankUserId={}", activeCredentials.size(), request.getBankUserId());
        return activeCredentials.size();
    }

    @Override
    @Transactional
    public ValidateCredentialResponse validate(ValidateCredentialRequest request) {
        return credentialRepository.findById(request.getCredentialId())
                .map(entity -> validateCredential(entity, request.getToken()))
                .orElseGet(() -> {
                    auditValidationFailure(request.getCredentialId(), null, null, "Credential not found");
                    return invalidResponse();
                });
    }

    @Override
    public CredentialStatusResponse getStatus(String credentialId) {
        SessionCredentialEntity entity = findCredentialOrThrow(credentialId);
        markExpiredIfNeeded(entity);
        return credentialMapper.toStatusResponse(entity);
    }

    @Override
    public Page<AuditEntryResponse> getAuditHistory(String credentialId, Pageable pageable) {
        if (!credentialRepository.existsById(credentialId)) {
            throw new CredentialNotFoundException(credentialId);
        }
        return auditEntryRepository.findByCredentialIdOrderByPerformedAtDesc(credentialId, pageable)
                .map(credentialMapper::toAuditResponse);
    }

    private ValidateCredentialResponse validateCredential(SessionCredentialEntity entity, String token) {
        markExpiredIfNeeded(entity);

        if (entity.getStatus() != CredentialStatus.ACTIVE) {
            auditValidationFailure(entity.getId(), entity.getChannelLinkId(), entity.getBankUserId(),
                    "Status " + entity.getStatus());
            return invalidResponse();
        }

        if (!tokenService.matches(token, entity.getTokenHash())) {
            auditValidationFailure(entity.getId(), entity.getChannelLinkId(), entity.getBankUserId(),
                    "Token mismatch");
            return invalidResponse();
        }

        audit(entity, AuditAction.VALIDATED, AuditActor.PRODUCT, null);

        return ValidateCredentialResponse.builder()
                .valid(true)
                .bankUserId(entity.getBankUserId())
                .channelLinkId(entity.getChannelLinkId())
                .expiresAt(entity.getExpiresAt())
                .build();
    }

    private void revokeExistingCredential(SessionCredentialEntity existing, RevokeReason reason, AuditActor actor) {
        applyRevocation(existing, reason, actor);
        audit(existing, AuditAction.REPLACED, actor, reason.name());
    }

    private void applyRevocation(SessionCredentialEntity entity, RevokeReason reason, AuditActor actor) {
        if (entity.getStatus() == CredentialStatus.REVOKED) {
            return;
        }
        entity.setStatus(CredentialStatus.REVOKED);
        entity.setRevokedAt(Instant.now());
        entity.setRevokeReason(reason);
        SessionCredentialEntity saved = credentialRepository.save(entity);
        audit(saved, AuditAction.REVOKED, actor, reason.name());
        log.info("Session credential revoked credentialId={} reason={}", saved.getId(), reason);
    }

    private void ensureActiveAndNotExpired(SessionCredentialEntity entity) {
        markExpiredIfNeeded(entity);
        if (entity.getStatus() == CredentialStatus.REVOKED) {
            throw new CredentialAlreadyRevokedException(entity.getId());
        }
        if (entity.getStatus() == CredentialStatus.EXPIRED) {
            throw new InvalidCredentialException("Session credential expired: " + entity.getId());
        }
        if (entity.getStatus() != CredentialStatus.ACTIVE) {
            throw new InvalidCredentialException("Session credential is not active: " + entity.getId());
        }
    }

    private void markExpiredIfNeeded(SessionCredentialEntity entity) {
        if (entity.getStatus() == CredentialStatus.ACTIVE
                && entity.getExpiresAt() != null
                && entity.getExpiresAt().isBefore(Instant.now())) {
            entity.setStatus(CredentialStatus.EXPIRED);
            credentialRepository.save(entity);
        }
    }

    private SessionCredentialEntity findCredentialOrThrow(String credentialId) {
        return credentialRepository.findById(credentialId)
                .orElseThrow(() -> new CredentialNotFoundException(credentialId));
    }

    private boolean isIdentityVerified(Boolean identityVerified) {
        return Boolean.TRUE.equals(identityVerified);
    }

    private void audit(SessionCredentialEntity entity, AuditAction action, AuditActor actor, String reason) {
        SessionAuditEntryEntity entry = SessionAuditEntryEntity.builder()
                .credentialId(entity.getId())
                .channelLinkId(entity.getChannelLinkId())
                .bankUserId(entity.getBankUserId())
                .action(action)
                .actor(actor)
                .reason(reason)
                .performedAt(Instant.now())
                .build();
        auditEntryRepository.save(entry);
    }

    private void auditValidationFailure(String credentialId, String channelLinkId, String bankUserId, String reason) {
        SessionAuditEntryEntity entry = SessionAuditEntryEntity.builder()
                .credentialId(Objects.requireNonNullElse(credentialId, "unknown"))
                .channelLinkId(channelLinkId)
                .bankUserId(bankUserId)
                .action(AuditAction.VALIDATION_FAILED)
                .actor(AuditActor.PRODUCT)
                .reason(reason)
                .performedAt(Instant.now())
                .build();
        auditEntryRepository.save(entry);
    }

    private ValidateCredentialResponse invalidResponse() {
        return ValidateCredentialResponse.builder()
                .valid(false)
                .build();
    }
}
