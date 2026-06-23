package com.wa.banking.channel.service;

import com.wa.banking.channel.api.v1.dto.PreferencesRequestV1;
import com.wa.banking.channel.api.v1.mapper.WhatsAppLinkMapper;
import com.wa.banking.channel.entity.InteractionHistoryEntity;
import com.wa.banking.channel.entity.InteractionType;
import com.wa.banking.channel.entity.LinkStatus;
import com.wa.banking.channel.entity.WhatsAppLinkEntity;
import com.wa.banking.channel.exception.DuplicateLinkException;
import com.wa.banking.channel.exception.InvalidLinkStateException;
import com.wa.banking.channel.exception.LinkNotFoundException;
import com.wa.banking.channel.exception.UserCannotLinkException;
import com.wa.banking.channel.exception.VerificationBlockedException;
import com.wa.banking.channel.integration.session.SessionClient;
import com.wa.banking.channel.integration.users.UsersClient;
import com.wa.banking.channel.repository.InteractionHistoryRepository;
import com.wa.banking.channel.repository.WhatsAppLinkRepository;
import com.wa.banking.channel.support.LinkTestFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WhatsAppLinkServiceImplTest {

    @Mock
    private WhatsAppLinkRepository linkRepository;

    @Mock
    private InteractionHistoryRepository interactionHistoryRepository;

    @Mock
    private UsersClient usersClient;

    @Mock
    private SessionClient sessionClient;

    @Mock
    private AuditService auditService;

    private final WhatsAppLinkMapper mapper = Mappers.getMapper(WhatsAppLinkMapper.class);

    private WhatsAppLinkServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new WhatsAppLinkServiceImpl(
                linkRepository,
                interactionHistoryRepository,
                usersClient,
                sessionClient,
                auditService,
                mapper);
    }

    @Test
    @DisplayName("Should return link when phone exists")
    void shouldReturnLink_whenPhoneExists() {
        WhatsAppLinkEntity entity = LinkTestFixtures.linkEntity(LinkStatus.ACTIVE);
        when(linkRepository.findByPhoneNumber(LinkTestFixtures.PHONE)).thenReturn(Optional.of(entity));

        var response = service.findByPhone(LinkTestFixtures.PHONE);

        assertThat(response.getPhoneNumber()).isEqualTo(LinkTestFixtures.PHONE);
        assertThat(response.getStatus()).isEqualTo(LinkStatus.ACTIVE);
    }

    @Test
    @DisplayName("Should throw LinkNotFoundException when phone missing")
    void shouldThrowLinkNotFound_whenPhoneMissing() {
        when(linkRepository.findByPhoneNumber(anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findByPhone("+549999999999"))
                .isInstanceOf(LinkNotFoundException.class);
    }

    @Test
    @DisplayName("Should create link when initiating new phone number")
    void shouldCreateLink_whenInitiateNewPhone() {
        when(linkRepository.existsByPhoneNumberAndStatus(LinkTestFixtures.PHONE, LinkStatus.ACTIVE))
                .thenReturn(false);
        when(linkRepository.findByPhoneNumber(LinkTestFixtures.PHONE)).thenReturn(Optional.empty());
        when(linkRepository.save(any(WhatsAppLinkEntity.class))).thenAnswer(invocation -> {
            WhatsAppLinkEntity saved = invocation.getArgument(0);
            saved.setId(LinkTestFixtures.LINK_ID);
            return saved;
        });

        var response = service.initiateLink(LinkTestFixtures.initiateRequest());

        assertThat(response.getId()).isEqualTo(LinkTestFixtures.LINK_ID);
        assertThat(response.getStatus()).isEqualTo(LinkStatus.NO_LINK);
        verify(auditService).recordInteraction(eq(LinkTestFixtures.LINK_ID),
                eq(InteractionType.ONBOARDING_STARTED), eq("SUCCESS"), anyString());
    }

    @Test
    @DisplayName("Should throw DuplicateLinkException when active link exists")
    void shouldThrowDuplicate_whenActiveLinkExists() {
        when(linkRepository.existsByPhoneNumberAndStatus(LinkTestFixtures.PHONE, LinkStatus.ACTIVE))
                .thenReturn(true);

        assertThatThrownBy(() -> service.initiateLink(LinkTestFixtures.initiateRequest()))
                .isInstanceOf(DuplicateLinkException.class);
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException when unlinked record exists")
    void shouldThrowBadRequest_whenUnlinkedRecordExists() {
        WhatsAppLinkEntity unlinked = LinkTestFixtures.linkEntity(LinkStatus.UNLINKED);
        when(linkRepository.existsByPhoneNumberAndStatus(LinkTestFixtures.PHONE, LinkStatus.ACTIVE))
                .thenReturn(false);
        when(linkRepository.findByPhoneNumber(LinkTestFixtures.PHONE)).thenReturn(Optional.of(unlinked));

        assertThatThrownBy(() -> service.initiateLink(LinkTestFixtures.initiateRequest()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("relink");
    }

    @Test
    @DisplayName("Should accept terms when link is NO_LINK")
    void shouldAcceptTerms_whenNoLinkStatus() {
        WhatsAppLinkEntity link = LinkTestFixtures.linkEntity(LinkStatus.NO_LINK);
        when(linkRepository.findById(LinkTestFixtures.LINK_ID)).thenReturn(Optional.of(link));
        when(linkRepository.save(any(WhatsAppLinkEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.acceptTerms(LinkTestFixtures.LINK_ID, LinkTestFixtures.acceptTermsRequest());

        assertThat(response.getStatus()).isEqualTo(LinkStatus.PENDING_VERIFICATION);
        assertThat(response.getTermsAcceptedAt()).isNotNull();
    }

    @Test
    @DisplayName("Should throw InvalidLinkStateException when accept-terms on wrong status")
    void shouldThrowInvalidState_whenAcceptTermsWrongStatus() {
        WhatsAppLinkEntity link = LinkTestFixtures.linkEntity(LinkStatus.ACTIVE);
        when(linkRepository.findById(LinkTestFixtures.LINK_ID)).thenReturn(Optional.of(link));

        assertThatThrownBy(() -> service.acceptTerms(LinkTestFixtures.LINK_ID, LinkTestFixtures.acceptTermsRequest()))
                .isInstanceOf(InvalidLinkStateException.class);
    }

    @Test
    @DisplayName("Should verify identity when OTP and user are valid")
    void shouldVerifyIdentity_whenOtpAndUserValid() {
        WhatsAppLinkEntity link = LinkTestFixtures.linkEntity(LinkStatus.PENDING_VERIFICATION);
        when(linkRepository.findById(LinkTestFixtures.LINK_ID)).thenReturn(Optional.of(link));
        when(usersClient.findByDocument(any(), anyString())).thenReturn(Optional.of(LinkTestFixtures.bankUser()));
        when(linkRepository.existsByPhoneNumberAndStatus(LinkTestFixtures.PHONE, LinkStatus.ACTIVE))
                .thenReturn(false);
        when(linkRepository.save(any(WhatsAppLinkEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.verifyIdentity(LinkTestFixtures.LINK_ID, LinkTestFixtures.verifyRequest());

        assertThat(response.isIdentityVerified()).isTrue();
        assertThat(response.getBankUserId()).isEqualTo(LinkTestFixtures.BANK_USER_ID);
    }

    @Test
    @DisplayName("Should throw UserCannotLinkException when H1 user not found")
    void shouldThrowUserCannotLink_whenUserNotFound() {
        WhatsAppLinkEntity link = LinkTestFixtures.linkEntity(LinkStatus.PENDING_VERIFICATION);
        when(linkRepository.findById(LinkTestFixtures.LINK_ID)).thenReturn(Optional.of(link));
        when(usersClient.findByDocument(any(), anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.verifyIdentity(LinkTestFixtures.LINK_ID, LinkTestFixtures.verifyRequest()))
                .isInstanceOf(UserCannotLinkException.class);
    }

    @Test
    @DisplayName("Should throw UserCannotLinkException when user cannot link channel")
    void shouldThrowUserCannotLink_whenUserInactive() {
        WhatsAppLinkEntity link = LinkTestFixtures.linkEntity(LinkStatus.PENDING_VERIFICATION);
        var inactiveUser = LinkTestFixtures.bankUser();
        inactiveUser.setCanLinkChannel(false);
        when(linkRepository.findById(LinkTestFixtures.LINK_ID)).thenReturn(Optional.of(link));
        when(usersClient.findByDocument(any(), anyString())).thenReturn(Optional.of(inactiveUser));

        assertThatThrownBy(() -> service.verifyIdentity(LinkTestFixtures.LINK_ID, LinkTestFixtures.verifyRequest()))
                .isInstanceOf(UserCannotLinkException.class);
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException when OTP is invalid")
    void shouldThrowBadRequest_whenOtpInvalid() {
        WhatsAppLinkEntity link = LinkTestFixtures.linkEntity(LinkStatus.PENDING_VERIFICATION);
        when(linkRepository.findById(LinkTestFixtures.LINK_ID)).thenReturn(Optional.of(link));
        when(usersClient.findByDocument(any(), anyString())).thenReturn(Optional.of(LinkTestFixtures.bankUser()));
        when(linkRepository.save(any(WhatsAppLinkEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var invalidOtp = LinkTestFixtures.verifyRequest();
        invalidOtp.setOtpCode("000000");

        assertThatThrownBy(() -> service.verifyIdentity(LinkTestFixtures.LINK_ID, invalidOtp))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid verification code");
    }

    @Test
    @DisplayName("Should block verification after max failed attempts")
    void shouldBlockVerification_whenMaxAttemptsReached() {
        WhatsAppLinkEntity link = LinkTestFixtures.linkEntity(LinkStatus.PENDING_VERIFICATION);
        link.setVerificationAttempts(2);
        when(linkRepository.findById(LinkTestFixtures.LINK_ID)).thenReturn(Optional.of(link));
        when(usersClient.findByDocument(any(), anyString())).thenReturn(Optional.of(LinkTestFixtures.bankUser()));
        when(linkRepository.save(any(WhatsAppLinkEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var invalidOtp = LinkTestFixtures.verifyRequest();
        invalidOtp.setOtpCode("000000");

        assertThatThrownBy(() -> service.verifyIdentity(LinkTestFixtures.LINK_ID, invalidOtp))
                .isInstanceOf(IllegalArgumentException.class);

        ArgumentCaptor<WhatsAppLinkEntity> captor = ArgumentCaptor.forClass(WhatsAppLinkEntity.class);
        verify(linkRepository).save(captor.capture());
        assertThat(captor.getValue().getVerificationAttempts()).isEqualTo(3);
        assertThat(captor.getValue().getVerificationBlockedUntil()).isNotNull();
    }

    @Test
    @DisplayName("Should throw VerificationBlockedException when block window active")
    void shouldThrowVerificationBlocked_whenBlockedUntilFuture() {
        WhatsAppLinkEntity link = LinkTestFixtures.linkEntity(LinkStatus.PENDING_VERIFICATION);
        link.setVerificationBlockedUntil(Instant.now().plus(10, ChronoUnit.MINUTES));
        when(linkRepository.findById(LinkTestFixtures.LINK_ID)).thenReturn(Optional.of(link));

        assertThatThrownBy(() -> service.verifyIdentity(LinkTestFixtures.LINK_ID, LinkTestFixtures.verifyRequest()))
                .isInstanceOf(VerificationBlockedException.class);
    }

    @Test
    @DisplayName("Should complete onboarding when identity verified")
    void shouldCompleteOnboarding_whenIdentityVerified() {
        WhatsAppLinkEntity link = LinkTestFixtures.linkEntity(LinkStatus.PENDING_VERIFICATION);
        link.setIdentityVerified(true);
        link.setBankUserId(LinkTestFixtures.BANK_USER_ID);
        when(linkRepository.findById(LinkTestFixtures.LINK_ID)).thenReturn(Optional.of(link));
        when(linkRepository.existsByPhoneNumberAndStatus(LinkTestFixtures.PHONE, LinkStatus.ACTIVE))
                .thenReturn(false);
        when(sessionClient.issueCredential(LinkTestFixtures.LINK_ID, LinkTestFixtures.BANK_USER_ID))
                .thenReturn(LinkTestFixtures.sessionCredential());
        when(linkRepository.save(any(WhatsAppLinkEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.completeOnboarding(LinkTestFixtures.LINK_ID);

        assertThat(response.getStatus()).isEqualTo(LinkStatus.ACTIVE);
        verify(sessionClient).issueCredential(LinkTestFixtures.LINK_ID, LinkTestFixtures.BANK_USER_ID);
    }

    @Test
    @DisplayName("Should throw InvalidLinkStateException when completing without verification")
    void shouldThrowInvalidState_whenCompleteWithoutVerification() {
        WhatsAppLinkEntity link = LinkTestFixtures.linkEntity(LinkStatus.PENDING_VERIFICATION);
        link.setIdentityVerified(false);
        when(linkRepository.findById(LinkTestFixtures.LINK_ID)).thenReturn(Optional.of(link));

        assertThatThrownBy(() -> service.completeOnboarding(LinkTestFixtures.LINK_ID))
                .isInstanceOf(InvalidLinkStateException.class);
    }

    @Test
    @DisplayName("Should return masked profile when link is active")
    void shouldReturnMaskedProfile_whenLinkActive() {
        WhatsAppLinkEntity link = LinkTestFixtures.activeLink();
        when(linkRepository.findById(LinkTestFixtures.LINK_ID)).thenReturn(Optional.of(link));
        when(usersClient.findById(LinkTestFixtures.BANK_USER_ID)).thenReturn(Optional.of(LinkTestFixtures.bankUser()));

        var profile = service.getProfile(LinkTestFixtures.LINK_ID);

        assertThat(profile.getDisplayName()).isEqualTo("María García");
        assertThat(profile.getMaskedEmail()).contains("***");
        assertThat(profile.getMaskedPhone()).contains("****");
    }

    @Test
    @DisplayName("Should throw InvalidLinkStateException when getting profile on inactive link")
    void shouldThrowInvalidState_whenProfileOnInactiveLink() {
        WhatsAppLinkEntity link = LinkTestFixtures.linkEntity(LinkStatus.NO_LINK);
        when(linkRepository.findById(LinkTestFixtures.LINK_ID)).thenReturn(Optional.of(link));

        assertThatThrownBy(() -> service.getProfile(LinkTestFixtures.LINK_ID))
                .isInstanceOf(InvalidLinkStateException.class);
    }

    @Test
    @DisplayName("Should update preferences when link is active")
    void shouldUpdatePreferences_whenLinkActive() {
        WhatsAppLinkEntity link = LinkTestFixtures.activeLink();
        when(linkRepository.findById(LinkTestFixtures.LINK_ID)).thenReturn(Optional.of(link));
        when(linkRepository.save(any(WhatsAppLinkEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var request = PreferencesRequestV1.builder()
                .language("en")
                .notificationsEnabled(false)
                .quietHoursStart("22:00")
                .quietHoursEnd("08:00")
                .build();

        var response = service.updatePreferences(LinkTestFixtures.LINK_ID, request);

        assertThat(response.getLanguage()).isEqualTo("en");
        assertThat(response.isNotificationsEnabled()).isFalse();
        assertThat(response.getQuietHoursStart()).isEqualTo("22:00");
    }

    @Test
    @DisplayName("Should block link and revoke H2 credential")
    void shouldBlockLinkAndRevokeCredential_whenActive() {
        WhatsAppLinkEntity link = LinkTestFixtures.activeLink();
        when(linkRepository.findById(LinkTestFixtures.LINK_ID)).thenReturn(Optional.of(link));
        when(linkRepository.save(any(WhatsAppLinkEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.block(LinkTestFixtures.LINK_ID, LinkTestFixtures.blockRequest());

        assertThat(response.getStatus()).isEqualTo(LinkStatus.BLOCKED);
        verify(sessionClient).revokeCredential(LinkTestFixtures.CREDENTIAL_ID);
    }

    @Test
    @DisplayName("Should unlink active link and revoke credential")
    void shouldUnlinkAndRevokeCredential_whenActive() {
        WhatsAppLinkEntity link = LinkTestFixtures.activeLink();
        when(linkRepository.findById(LinkTestFixtures.LINK_ID)).thenReturn(Optional.of(link));
        when(linkRepository.save(any(WhatsAppLinkEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.unlink(LinkTestFixtures.LINK_ID, LinkTestFixtures.unlinkRequest());

        assertThat(response.getStatus()).isEqualTo(LinkStatus.UNLINKED);
        assertThat(response.isIdentityVerified()).isFalse();
        verify(sessionClient).revokeCredential(LinkTestFixtures.CREDENTIAL_ID);
    }

    @Test
    @DisplayName("Should relink when link is unlinked")
    void shouldRelink_whenUnlinkedStatus() {
        WhatsAppLinkEntity link = LinkTestFixtures.linkEntity(LinkStatus.UNLINKED);
        when(linkRepository.findById(LinkTestFixtures.LINK_ID)).thenReturn(Optional.of(link));
        when(linkRepository.save(any(WhatsAppLinkEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.relink(LinkTestFixtures.LINK_ID, LinkTestFixtures.relinkRequest());

        assertThat(response.getStatus()).isEqualTo(LinkStatus.NO_LINK);
        assertThat(response.getBankUserId()).isNull();
        verify(sessionClient, never()).revokeCredential(anyString());
    }

    @Test
    @DisplayName("Should return interaction history page")
    void shouldReturnHistory_whenLinkExists() {
        WhatsAppLinkEntity link = LinkTestFixtures.activeLink();
        InteractionHistoryEntity history = InteractionHistoryEntity.builder()
                .linkId(LinkTestFixtures.LINK_ID)
                .type(InteractionType.PROFILE_VIEWED)
                .result("SUCCESS")
                .summary("Profile viewed")
                .build();
        when(linkRepository.findById(LinkTestFixtures.LINK_ID)).thenReturn(Optional.of(link));
        when(interactionHistoryRepository.findByLinkIdOrderByOccurredAtDesc(
                eq(LinkTestFixtures.LINK_ID), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of(history)));

        Page<?> page = service.getHistory(LinkTestFixtures.LINK_ID, PageRequest.of(0, 20));

        assertThat(page.getTotalElements()).isEqualTo(1);
    }
}
