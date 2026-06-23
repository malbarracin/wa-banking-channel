package com.wa.banking.auth.service;

import com.wa.banking.auth.api.v1.dto.IssueCredentialRequest;
import com.wa.banking.auth.api.v1.dto.IssueCredentialResponse;
import com.wa.banking.auth.api.v1.dto.RenewCredentialResponse;
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
import com.wa.banking.auth.support.SessionCredentialTestFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SessionCredentialServiceTest {

    @Mock
    private SessionCredentialRepository credentialRepository;

    @Mock
    private SessionAuditEntryRepository auditEntryRepository;

    @Mock
    private CredentialTokenService tokenService;

    @Mock
    private UsersClient usersClient;

    private final SessionCredentialProperties properties = new SessionCredentialProperties();
    private final SessionCredentialMapper mapper = Mappers.getMapper(SessionCredentialMapper.class);

    private SessionCredentialServiceImpl service;

    @BeforeEach
    void setUp() {
        properties.setTtlHours(24);
        properties.setPepper("test-pepper");
        service = new SessionCredentialServiceImpl(
                credentialRepository,
                auditEntryRepository,
                mapper,
                tokenService,
                properties,
                usersClient);
    }

    @Test
    @DisplayName("Should issue credential when identity is verified")
    void shouldIssueCredential_whenIdentityVerified() {
        IssueCredentialRequest request = SessionCredentialTestFixtures.issueRequest();
        when(credentialRepository.findByChannelLinkIdAndStatus(
                SessionCredentialTestFixtures.CHANNEL_LINK_ID, CredentialStatus.ACTIVE))
                .thenReturn(Optional.empty());
        when(tokenService.generateToken()).thenReturn(SessionCredentialTestFixtures.TOKEN);
        when(tokenService.hashToken(SessionCredentialTestFixtures.TOKEN))
                .thenReturn(SessionCredentialTestFixtures.TOKEN_HASH);
        when(credentialRepository.save(any(SessionCredentialEntity.class))).thenAnswer(invocation -> {
            SessionCredentialEntity entity = invocation.getArgument(0);
            entity.setId(SessionCredentialTestFixtures.CREDENTIAL_ID);
            return entity;
        });

        IssueCredentialResponse response = service.issue(request);

        assertThat(response.getCredentialId()).isEqualTo(SessionCredentialTestFixtures.CREDENTIAL_ID);
        assertThat(response.getToken()).isEqualTo(SessionCredentialTestFixtures.TOKEN);
        assertThat(response.getExpiresAt()).isAfter(Instant.now());

        ArgumentCaptor<SessionAuditEntryEntity> auditCaptor = ArgumentCaptor.forClass(SessionAuditEntryEntity.class);
        verify(auditEntryRepository).save(auditCaptor.capture());
        assertThat(auditCaptor.getValue().getAction()).isEqualTo(AuditAction.ISSUED);
        assertThat(auditCaptor.getValue().getActor()).isEqualTo(AuditActor.CHANNEL);
    }

    @Test
    @DisplayName("Should throw VerificationRequiredException when identity not verified")
    void shouldThrowVerificationRequired_whenIdentityNotVerified() {
        IssueCredentialRequest request = SessionCredentialTestFixtures.issueRequestUnverified();

        assertThatThrownBy(() -> service.issue(request))
                .isInstanceOf(VerificationRequiredException.class);

        verify(credentialRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should replace active credential when one already exists for channel link")
    void shouldReplaceActiveCredential_whenExistingActive() {
        IssueCredentialRequest request = SessionCredentialTestFixtures.issueRequest();
        SessionCredentialEntity existing = SessionCredentialTestFixtures.activeCredential();
        existing.setId("cred-old");

        when(credentialRepository.findByChannelLinkIdAndStatus(
                SessionCredentialTestFixtures.CHANNEL_LINK_ID, CredentialStatus.ACTIVE))
                .thenReturn(Optional.of(existing));
        when(tokenService.generateToken()).thenReturn(SessionCredentialTestFixtures.TOKEN);
        when(tokenService.hashToken(SessionCredentialTestFixtures.TOKEN))
                .thenReturn(SessionCredentialTestFixtures.TOKEN_HASH);
        when(credentialRepository.save(any(SessionCredentialEntity.class))).thenAnswer(invocation -> {
            SessionCredentialEntity entity = invocation.getArgument(0);
            if (entity.getId() == null) {
                entity.setId(SessionCredentialTestFixtures.CREDENTIAL_ID);
            }
            return entity;
        });

        service.issue(request);

        assertThat(existing.getStatus()).isEqualTo(CredentialStatus.REVOKED);
        verify(auditEntryRepository, times(3)).save(any(SessionAuditEntryEntity.class));
    }

    @Test
    @DisplayName("Should renew credential when active and not expired")
    void shouldRenewCredential_whenActive() {
        SessionCredentialEntity entity = SessionCredentialTestFixtures.activeCredential();
        when(credentialRepository.findById(SessionCredentialTestFixtures.CREDENTIAL_ID))
                .thenReturn(Optional.of(entity));
        when(credentialRepository.save(entity)).thenReturn(entity);

        RenewCredentialResponse response = service.renew(SessionCredentialTestFixtures.CREDENTIAL_ID);

        assertThat(response.getCredentialId()).isEqualTo(SessionCredentialTestFixtures.CREDENTIAL_ID);
        assertThat(entity.getRenewalCount()).isEqualTo(1);
        assertThat(entity.getExpiresAt()).isAfter(Instant.now());

        ArgumentCaptor<SessionAuditEntryEntity> auditCaptor = ArgumentCaptor.forClass(SessionAuditEntryEntity.class);
        verify(auditEntryRepository).save(auditCaptor.capture());
        assertThat(auditCaptor.getValue().getAction()).isEqualTo(AuditAction.RENEWED);
    }

    @Test
    @DisplayName("Should throw InvalidCredentialException when renewing expired credential")
    void shouldThrowInvalidCredential_whenRenewExpired() {
        SessionCredentialEntity entity = SessionCredentialTestFixtures.expiredCredential();
        when(credentialRepository.findById(SessionCredentialTestFixtures.CREDENTIAL_ID))
                .thenReturn(Optional.of(entity));
        when(credentialRepository.save(entity)).thenReturn(entity);

        assertThatThrownBy(() -> service.renew(SessionCredentialTestFixtures.CREDENTIAL_ID))
                .isInstanceOf(InvalidCredentialException.class);

        assertThat(entity.getStatus()).isEqualTo(CredentialStatus.EXPIRED);
    }

    @Test
    @DisplayName("Should revoke credential immediately when active")
    void shouldRevokeCredential_whenActive() {
        SessionCredentialEntity entity = SessionCredentialTestFixtures.activeCredential();
        when(credentialRepository.findById(SessionCredentialTestFixtures.CREDENTIAL_ID))
                .thenReturn(Optional.of(entity));
        when(credentialRepository.save(entity)).thenReturn(entity);

        service.revoke(SessionCredentialTestFixtures.CREDENTIAL_ID, RevokeReason.POLICY, AuditActor.CHANNEL);

        assertThat(entity.getStatus()).isEqualTo(CredentialStatus.REVOKED);
        assertThat(entity.getRevokeReason()).isEqualTo(RevokeReason.POLICY);
        assertThat(entity.getRevokedAt()).isNotNull();

        ArgumentCaptor<SessionAuditEntryEntity> auditCaptor = ArgumentCaptor.forClass(SessionAuditEntryEntity.class);
        verify(auditEntryRepository).save(auditCaptor.capture());
        assertThat(auditCaptor.getValue().getAction()).isEqualTo(AuditAction.REVOKED);
    }

    @Test
    @DisplayName("Should skip revoke when credential already revoked")
    void shouldSkipRevoke_whenAlreadyRevoked() {
        SessionCredentialEntity entity = SessionCredentialTestFixtures.revokedCredential();
        when(credentialRepository.findById(SessionCredentialTestFixtures.CREDENTIAL_ID))
                .thenReturn(Optional.of(entity));

        service.revoke(SessionCredentialTestFixtures.CREDENTIAL_ID, RevokeReason.POLICY, AuditActor.CHANNEL);

        verify(credentialRepository, never()).save(any());
        verify(auditEntryRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should return valid response when token matches active credential")
    void shouldReturnValid_whenValidateOk() {
        SessionCredentialEntity entity = SessionCredentialTestFixtures.activeCredential();
        ValidateCredentialRequest request = SessionCredentialTestFixtures.validateRequest();
        when(credentialRepository.findById(SessionCredentialTestFixtures.CREDENTIAL_ID))
                .thenReturn(Optional.of(entity));
        when(tokenService.matches(SessionCredentialTestFixtures.TOKEN, SessionCredentialTestFixtures.TOKEN_HASH))
                .thenReturn(true);

        ValidateCredentialResponse response = service.validate(request);

        assertThat(response.isValid()).isTrue();
        assertThat(response.getBankUserId()).isEqualTo(SessionCredentialTestFixtures.BANK_USER_ID);
        assertThat(response.getChannelLinkId()).isEqualTo(SessionCredentialTestFixtures.CHANNEL_LINK_ID);

        ArgumentCaptor<SessionAuditEntryEntity> auditCaptor = ArgumentCaptor.forClass(SessionAuditEntryEntity.class);
        verify(auditEntryRepository).save(auditCaptor.capture());
        assertThat(auditCaptor.getValue().getAction()).isEqualTo(AuditAction.VALIDATED);
    }

    @Test
    @DisplayName("Should return invalid when credential is revoked")
    void shouldReturnInvalid_whenValidateRevoked() {
        SessionCredentialEntity entity = SessionCredentialTestFixtures.revokedCredential();
        ValidateCredentialRequest request = SessionCredentialTestFixtures.validateRequest();
        when(credentialRepository.findById(SessionCredentialTestFixtures.CREDENTIAL_ID))
                .thenReturn(Optional.of(entity));

        ValidateCredentialResponse response = service.validate(request);

        assertThat(response.isValid()).isFalse();
        verify(tokenService, never()).matches(any(), any());

        ArgumentCaptor<SessionAuditEntryEntity> auditCaptor = ArgumentCaptor.forClass(SessionAuditEntryEntity.class);
        verify(auditEntryRepository).save(auditCaptor.capture());
        assertThat(auditCaptor.getValue().getAction()).isEqualTo(AuditAction.VALIDATION_FAILED);
    }

    @Test
    @DisplayName("Should return invalid when token does not match")
    void shouldReturnInvalid_whenValidateWrongToken() {
        SessionCredentialEntity entity = SessionCredentialTestFixtures.activeCredential();
        ValidateCredentialRequest request = SessionCredentialTestFixtures.validateRequest();
        when(credentialRepository.findById(SessionCredentialTestFixtures.CREDENTIAL_ID))
                .thenReturn(Optional.of(entity));
        when(tokenService.matches(SessionCredentialTestFixtures.TOKEN, SessionCredentialTestFixtures.TOKEN_HASH))
                .thenReturn(false);

        ValidateCredentialResponse response = service.validate(request);

        assertThat(response.isValid()).isFalse();

        ArgumentCaptor<SessionAuditEntryEntity> auditCaptor = ArgumentCaptor.forClass(SessionAuditEntryEntity.class);
        verify(auditEntryRepository).save(auditCaptor.capture());
        assertThat(auditCaptor.getValue().getAction()).isEqualTo(AuditAction.VALIDATION_FAILED);
        assertThat(auditCaptor.getValue().getReason()).contains("Token mismatch");
    }

    @Test
    @DisplayName("Should return invalid when credential is expired")
    void shouldReturnInvalid_whenValidateExpired() {
        SessionCredentialEntity entity = SessionCredentialTestFixtures.expiredCredential();
        ValidateCredentialRequest request = SessionCredentialTestFixtures.validateRequest();
        when(credentialRepository.findById(SessionCredentialTestFixtures.CREDENTIAL_ID))
                .thenReturn(Optional.of(entity));
        when(credentialRepository.save(entity)).thenReturn(entity);

        ValidateCredentialResponse response = service.validate(request);

        assertThat(response.isValid()).isFalse();
        assertThat(entity.getStatus()).isEqualTo(CredentialStatus.EXPIRED);
    }

    @Test
    @DisplayName("Should return invalid when credential not found")
    void shouldReturnInvalid_whenCredentialNotFound() {
        ValidateCredentialRequest request = SessionCredentialTestFixtures.validateRequest();
        when(credentialRepository.findById(SessionCredentialTestFixtures.CREDENTIAL_ID))
                .thenReturn(Optional.empty());

        ValidateCredentialResponse response = service.validate(request);

        assertThat(response.isValid()).isFalse();

        ArgumentCaptor<SessionAuditEntryEntity> auditCaptor = ArgumentCaptor.forClass(SessionAuditEntryEntity.class);
        verify(auditEntryRepository).save(auditCaptor.capture());
        assertThat(auditCaptor.getValue().getAction()).isEqualTo(AuditAction.VALIDATION_FAILED);
    }

    @Test
    @DisplayName("Should revoke all active credentials for user")
    void shouldRevokeAllActive_whenRevokeByUser() {
        SessionCredentialEntity first = SessionCredentialTestFixtures.activeCredential();
        first.setId("cred-1");
        SessionCredentialEntity second = SessionCredentialTestFixtures.activeCredential();
        second.setId("cred-2");
        when(credentialRepository.findByBankUserIdAndStatus(
                SessionCredentialTestFixtures.BANK_USER_ID, CredentialStatus.ACTIVE))
                .thenReturn(List.of(first, second));
        when(credentialRepository.save(any(SessionCredentialEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        int revokedCount = service.revokeByUser(SessionCredentialTestFixtures.revokeByUserRequest());

        assertThat(revokedCount).isEqualTo(2);
        assertThat(first.getStatus()).isEqualTo(CredentialStatus.REVOKED);
        assertThat(second.getStatus()).isEqualTo(CredentialStatus.REVOKED);
        verify(auditEntryRepository, times(2)).save(any(SessionAuditEntryEntity.class));
    }

    @Test
    @DisplayName("Should return zero when no active credentials for user")
    void shouldReturnZero_whenRevokeByUserNoActive() {
        when(credentialRepository.findByBankUserIdAndStatus(
                SessionCredentialTestFixtures.BANK_USER_ID, CredentialStatus.ACTIVE))
                .thenReturn(List.of());

        int revokedCount = service.revokeByUser(SessionCredentialTestFixtures.revokeByUserRequest());

        assertThat(revokedCount).isZero();
        verify(credentialRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should throw CredentialNotFoundException when renewing unknown credential")
    void shouldThrowNotFound_whenRenewUnknownId() {
        when(credentialRepository.findById("unknown")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.renew("unknown"))
                .isInstanceOf(CredentialNotFoundException.class);
    }

    @Test
    @DisplayName("Should throw CredentialAlreadyRevokedException when renewing revoked credential")
    void shouldThrowAlreadyRevoked_whenRenewRevoked() {
        SessionCredentialEntity entity = SessionCredentialTestFixtures.revokedCredential();
        when(credentialRepository.findById(SessionCredentialTestFixtures.CREDENTIAL_ID))
                .thenReturn(Optional.of(entity));

        assertThatThrownBy(() -> service.renew(SessionCredentialTestFixtures.CREDENTIAL_ID))
                .isInstanceOf(CredentialAlreadyRevokedException.class);
    }

    @Test
    @DisplayName("Should return status and mark expired when TTL passed")
    void shouldReturnStatus_whenCredentialExpired() {
        SessionCredentialEntity entity = SessionCredentialTestFixtures.expiredCredential();
        when(credentialRepository.findById(SessionCredentialTestFixtures.CREDENTIAL_ID))
                .thenReturn(Optional.of(entity));
        when(credentialRepository.save(entity)).thenReturn(entity);

        var status = service.getStatus(SessionCredentialTestFixtures.CREDENTIAL_ID);

        assertThat(status.getCredentialId()).isEqualTo(SessionCredentialTestFixtures.CREDENTIAL_ID);
        assertThat(entity.getStatus()).isEqualTo(CredentialStatus.EXPIRED);
    }

    @Test
    @DisplayName("Should return audit history when credential exists")
    void shouldReturnAuditHistory_whenCredentialExists() {
        when(credentialRepository.existsById(SessionCredentialTestFixtures.CREDENTIAL_ID)).thenReturn(true);
        SessionAuditEntryEntity entry = SessionAuditEntryEntity.builder()
                .credentialId(SessionCredentialTestFixtures.CREDENTIAL_ID)
                .action(AuditAction.ISSUED)
                .performedAt(Instant.now())
                .build();
        when(auditEntryRepository.findByCredentialIdOrderByPerformedAtDesc(
                eq(SessionCredentialTestFixtures.CREDENTIAL_ID), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of(entry)));

        var page = service.getAuditHistory(SessionCredentialTestFixtures.CREDENTIAL_ID, PageRequest.of(0, 20));

        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getContent().getFirst().getAction()).isEqualTo(AuditAction.ISSUED);
    }

    @Test
    @DisplayName("Should throw CredentialNotFoundException when audit history for unknown credential")
    void shouldThrowNotFound_whenAuditHistoryUnknownCredential() {
        when(credentialRepository.existsById("unknown")).thenReturn(false);

        assertThatThrownBy(() -> service.getAuditHistory("unknown", PageRequest.of(0, 20)))
                .isInstanceOf(CredentialNotFoundException.class);
    }
}
