package com.wa.banking.channel.service;

import com.wa.banking.channel.api.v1.dto.AcceptTermsRequestV1;
import com.wa.banking.channel.api.v1.dto.BlockLinkRequestV1;
import com.wa.banking.channel.api.v1.dto.InitiateLinkRequestV1;
import com.wa.banking.channel.api.v1.dto.InteractionHistoryItemV1;
import com.wa.banking.channel.api.v1.dto.LinkResponseV1;
import com.wa.banking.channel.api.v1.dto.PreferencesRequestV1;
import com.wa.banking.channel.api.v1.dto.PreferencesResponseV1;
import com.wa.banking.channel.api.v1.dto.ProfileResponseV1;
import com.wa.banking.channel.api.v1.dto.RelinkRequestV1;
import com.wa.banking.channel.api.v1.dto.UnlinkRequestV1;
import com.wa.banking.channel.api.v1.dto.VerifyIdentityRequestV1;
import com.wa.banking.channel.api.v1.mapper.WhatsAppLinkMapper;
import com.wa.banking.channel.entity.AuditAction;
import com.wa.banking.channel.entity.ChannelPreferences;
import com.wa.banking.channel.entity.InteractionType;
import com.wa.banking.channel.entity.LinkStatus;
import com.wa.banking.channel.entity.WhatsAppLinkEntity;
import com.wa.banking.channel.exception.DuplicateLinkException;
import com.wa.banking.channel.exception.InvalidLinkStateException;
import com.wa.banking.channel.exception.LinkNotFoundException;
import com.wa.banking.channel.exception.UserCannotLinkException;
import com.wa.banking.channel.exception.VerificationBlockedException;
import com.wa.banking.channel.integration.session.SessionClient;
import com.wa.banking.channel.integration.session.SessionCredentialResponse;
import com.wa.banking.channel.integration.users.UserResponseV1;
import com.wa.banking.channel.integration.users.UsersClient;
import com.wa.banking.channel.repository.InteractionHistoryRepository;
import com.wa.banking.channel.repository.WhatsAppLinkRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.EnumSet;
import java.util.Set;

/**
 * Implementación del servicio de vínculo WhatsApp con reglas RN1–RN8.
 *
 * @author licius-it
 * @since 2026-06-23
 * @contact marceloalejandro.albarracin@gmail.com
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WhatsAppLinkServiceImpl implements WhatsAppLinkService {

    private static final int MAX_VERIFICATION_ATTEMPTS = 3;

    private static final int VERIFICATION_BLOCK_MINUTES = 30;

    private static final String OTP_MVP_CODE = "123456";

    private static final Set<LinkStatus> VERIFY_ALLOWED_STATUSES =
            EnumSet.of(LinkStatus.PENDING_VERIFICATION, LinkStatus.VERIFICATION_FAILED);

    private final WhatsAppLinkRepository linkRepository;

    private final InteractionHistoryRepository interactionHistoryRepository;

    private final UsersClient usersClient;

    private final SessionClient sessionClient;

    private final AuditService auditService;

    private final WhatsAppLinkMapper mapper;

    @Override
    public LinkResponseV1 findByPhone(String phone) {
        WhatsAppLinkEntity link = linkRepository.findByPhoneNumber(phone)
                .orElseThrow(() -> new LinkNotFoundException(phone));
        return mapper.toResponse(link);
    }

    @Override
    public LinkResponseV1 initiateLink(InitiateLinkRequestV1 request) {
        String phone = request.getPhoneNumber();

        if (linkRepository.existsByPhoneNumberAndStatus(phone, LinkStatus.ACTIVE)) {
            throw new DuplicateLinkException(phone);
        }

        linkRepository.findByPhoneNumber(phone).ifPresent(existing -> {
            if (existing.getStatus() == LinkStatus.UNLINKED) {
                throw new IllegalArgumentException(
                        "Phone number has a previous unlinked record; use relink on link id " + existing.getId());
            }
            if (existing.getStatus() != LinkStatus.NO_LINK
                    && existing.getStatus() != LinkStatus.VERIFICATION_FAILED) {
                throw new DuplicateLinkException(phone);
            }
        });

        WhatsAppLinkEntity link = linkRepository.findByPhoneNumber(phone)
                .orElseGet(() -> WhatsAppLinkEntity.builder()
                        .phoneNumber(phone)
                        .status(LinkStatus.NO_LINK)
                        .preferences(ChannelPreferences.builder().build())
                        .build());

        link.setStatus(LinkStatus.NO_LINK);
        link = linkRepository.save(link);

        auditService.recordInteraction(link.getId(), InteractionType.ONBOARDING_STARTED, "SUCCESS",
                "Onboarding started for channel link");

        log.info("Link initiated for phone ending in {}", maskPhone(phone));
        return mapper.toResponse(link);
    }

    @Override
    public LinkResponseV1 acceptTerms(String linkId, AcceptTermsRequestV1 request) {
        WhatsAppLinkEntity link = getLinkOrThrow(linkId);
        if (link.getStatus() != LinkStatus.NO_LINK) {
            throw new InvalidLinkStateException(link.getStatus(), "accept-terms");
        }

        link.setTermsAcceptedAt(Instant.now());
        link.setStatus(LinkStatus.PENDING_VERIFICATION);
        link = linkRepository.save(link);

        auditService.recordAudit(linkId, AuditAction.TERMS_ACCEPTED, "SUCCESS", "Terms accepted");
        auditService.recordInteraction(linkId, InteractionType.TERMS_ACCEPTED, "SUCCESS", "Terms accepted");

        return mapper.toResponse(link);
    }

    @Override
    public LinkResponseV1 verifyIdentity(String linkId, VerifyIdentityRequestV1 request) {
        WhatsAppLinkEntity link = getLinkOrThrow(linkId);
        if (!VERIFY_ALLOWED_STATUSES.contains(link.getStatus())) {
            throw new InvalidLinkStateException(link.getStatus(), "verify");
        }

        if (link.getVerificationBlockedUntil() != null
                && link.getVerificationBlockedUntil().isAfter(Instant.now())) {
            throw new VerificationBlockedException();
        }

        auditService.recordAudit(linkId, AuditAction.VERIFICATION_ATTEMPT, "STARTED", "Verification started");

        UserResponseV1 user = usersClient
                .findByDocument(request.getDocumentType(), request.getDocumentNumber())
                .orElseThrow(() -> {
                    auditService.recordAudit(linkId, AuditAction.VERIFICATION_ATTEMPT, "FAILED",
                            "User not found in H1");
                    auditService.recordInteraction(linkId, InteractionType.VERIFICATION, "FAILED",
                            "User not found in H1");
                    return new UserCannotLinkException("Bank user not found; cannot link channel");
                });

        if (!user.isCanLinkChannel()) {
            auditService.recordAudit(linkId, AuditAction.VERIFICATION_ATTEMPT, "FAILED",
                    "User cannot link channel");
            auditService.recordInteraction(linkId, InteractionType.VERIFICATION, "FAILED",
                    "User status does not allow channel linking");
            throw new UserCannotLinkException("Bank user is not active; cannot link channel");
        }

        if (!isOtpValid(request.getOtpCode())) {
            return handleFailedVerification(link);
        }

        if (linkRepository.existsByPhoneNumberAndStatus(link.getPhoneNumber(), LinkStatus.ACTIVE)) {
            throw new DuplicateLinkException(link.getPhoneNumber());
        }

        link.setBankUserId(user.getId());
        link.setDocumentType(request.getDocumentType());
        link.setDocumentNumber(request.getDocumentNumber());
        link.setIdentityVerified(true);
        link.setStatus(LinkStatus.PENDING_VERIFICATION);
        link.setVerificationAttempts(0);
        link.setVerificationBlockedUntil(null);
        link = linkRepository.save(link);

        auditService.recordAudit(linkId, AuditAction.VERIFICATION_ATTEMPT, "SUCCESS", "Identity verified");
        auditService.recordInteraction(linkId, InteractionType.VERIFICATION, "SUCCESS", "Identity verified");

        log.info("Identity verified for link {}", linkId);
        return mapper.toResponse(link);
    }

    @Override
    public LinkResponseV1 completeOnboarding(String linkId) {
        WhatsAppLinkEntity link = getLinkOrThrow(linkId);
        if (link.getStatus() != LinkStatus.PENDING_VERIFICATION || !link.isIdentityVerified()) {
            throw new InvalidLinkStateException(link.getStatus(), "complete-onboarding");
        }

        if (linkRepository.existsByPhoneNumberAndStatus(link.getPhoneNumber(), LinkStatus.ACTIVE)) {
            throw new DuplicateLinkException(link.getPhoneNumber());
        }

        SessionCredentialResponse credential = sessionClient.issueCredential(linkId, link.getBankUserId());
        link.setSessionCredentialId(credential.getCredentialId());
        link.setStatus(LinkStatus.ACTIVE);
        link.setDocumentType(null);
        link.setDocumentNumber(null);
        link = linkRepository.save(link);

        auditService.recordAudit(linkId, AuditAction.LINKED, "SUCCESS", "Link activated");
        auditService.recordAudit(linkId, AuditAction.CREDENTIAL_REQUESTED, "SUCCESS",
                "Credential issued");
        auditService.recordInteraction(linkId, InteractionType.LINK_COMPLETED, "SUCCESS",
                "Onboarding completed and link activated");

        log.info("Onboarding completed for link {}", linkId);
        return mapper.toResponse(link);
    }

    @Override
    public LinkResponseV1 findById(String linkId) {
        return mapper.toResponse(getLinkOrThrow(linkId));
    }

    @Override
    public ProfileResponseV1 getProfile(String linkId) {
        WhatsAppLinkEntity link = getLinkOrThrow(linkId);
        if (link.getStatus() != LinkStatus.ACTIVE) {
            throw new InvalidLinkStateException(link.getStatus(), "get-profile");
        }

        UserResponseV1 user = usersClient.findById(link.getBankUserId())
                .orElseThrow(() -> new UserCannotLinkException("Bank user not found"));

        auditService.recordInteraction(linkId, InteractionType.PROFILE_VIEWED, "SUCCESS", "Profile viewed");

        return ProfileResponseV1.builder()
                .linkId(linkId)
                .displayName(user.getDisplayName())
                .maskedEmail(maskEmail(user.getEmail()))
                .maskedPhone(maskPhone(user.getPhone()))
                .language(link.getPreferences().getLanguage())
                .build();
    }

    @Override
    public PreferencesResponseV1 getPreferences(String linkId) {
        WhatsAppLinkEntity link = getLinkOrThrow(linkId);
        if (link.getStatus() != LinkStatus.ACTIVE) {
            throw new InvalidLinkStateException(link.getStatus(), "get-preferences");
        }
        return mapper.toPreferencesResponse(link.getPreferences());
    }

    @Override
    public PreferencesResponseV1 updatePreferences(String linkId, PreferencesRequestV1 request) {
        WhatsAppLinkEntity link = getLinkOrThrow(linkId);
        if (link.getStatus() != LinkStatus.ACTIVE) {
            throw new InvalidLinkStateException(link.getStatus(), "update-preferences");
        }

        ChannelPreferences preferences = link.getPreferences();
        if (request.getLanguage() != null) {
            preferences.setLanguage(request.getLanguage());
        }
        if (request.getNotificationsEnabled() != null) {
            preferences.setNotificationsEnabled(request.getNotificationsEnabled());
        }
        if (request.getQuietHoursStart() != null) {
            preferences.setQuietHoursStart(request.getQuietHoursStart());
        }
        if (request.getQuietHoursEnd() != null) {
            preferences.setQuietHoursEnd(request.getQuietHoursEnd());
        }

        link.setPreferences(preferences);
        linkRepository.save(link);

        auditService.recordInteraction(linkId, InteractionType.PREFERENCES_UPDATED, "SUCCESS",
                "Channel preferences updated");

        return mapper.toPreferencesResponse(preferences);
    }

    @Override
    public LinkResponseV1 block(String linkId, BlockLinkRequestV1 request) {
        WhatsAppLinkEntity link = getLinkOrThrow(linkId);
        if (link.getStatus() != LinkStatus.ACTIVE) {
            throw new InvalidLinkStateException(link.getStatus(), "block");
        }

        revokeCredential(link);
        link.setStatus(LinkStatus.BLOCKED);
        link = linkRepository.save(link);

        auditService.recordAudit(linkId, AuditAction.BLOCKED, "SUCCESS", "Link blocked");
        auditService.recordAudit(linkId, AuditAction.CREDENTIAL_REVOKED, "SUCCESS", "Credential revoked");
        auditService.recordInteraction(linkId, InteractionType.BLOCKED, "SUCCESS", "Link blocked by user");

        return mapper.toResponse(link);
    }

    @Override
    public LinkResponseV1 unlink(String linkId, UnlinkRequestV1 request) {
        WhatsAppLinkEntity link = getLinkOrThrow(linkId);
        if (link.getStatus() != LinkStatus.ACTIVE && link.getStatus() != LinkStatus.BLOCKED) {
            throw new InvalidLinkStateException(link.getStatus(), "unlink");
        }

        revokeCredential(link);
        link.setStatus(LinkStatus.UNLINKED);
        link.setIdentityVerified(false);
        link.setSessionCredentialId(null);
        link = linkRepository.save(link);

        auditService.recordAudit(linkId, AuditAction.UNLINKED, "SUCCESS", "Link unlinked");
        auditService.recordAudit(linkId, AuditAction.CREDENTIAL_REVOKED, "SUCCESS", "Credential revoked");
        auditService.recordInteraction(linkId, InteractionType.UNLINKED, "SUCCESS", "Link unlinked by user");

        return mapper.toResponse(link);
    }

    @Override
    public LinkResponseV1 relink(String linkId, RelinkRequestV1 request) {
        WhatsAppLinkEntity link = getLinkOrThrow(linkId);
        if (link.getStatus() != LinkStatus.UNLINKED) {
            throw new InvalidLinkStateException(link.getStatus(), "relink");
        }

        link.setStatus(LinkStatus.NO_LINK);
        link.setBankUserId(null);
        link.setIdentityVerified(false);
        link.setVerificationAttempts(0);
        link.setVerificationBlockedUntil(null);
        link.setTermsAcceptedAt(null);
        link.setDocumentType(null);
        link.setDocumentNumber(null);
        link.setSessionCredentialId(null);
        link = linkRepository.save(link);

        auditService.recordAudit(linkId, AuditAction.RELINKED, "SUCCESS", "Relink started");
        auditService.recordInteraction(linkId, InteractionType.RELINK_STARTED, "SUCCESS",
                "Relink flow started; full verification required");

        return mapper.toResponse(link);
    }

    @Override
    public Page<InteractionHistoryItemV1> getHistory(String linkId, Pageable pageable) {
        getLinkOrThrow(linkId);
        return interactionHistoryRepository.findByLinkIdOrderByOccurredAtDesc(linkId, pageable)
                .map(mapper::toHistoryItem);
    }

    private LinkResponseV1 handleFailedVerification(WhatsAppLinkEntity link) {
        int attempts = link.getVerificationAttempts() + 1;
        link.setVerificationAttempts(attempts);
        link.setStatus(LinkStatus.VERIFICATION_FAILED);

        if (attempts >= MAX_VERIFICATION_ATTEMPTS) {
            link.setVerificationBlockedUntil(Instant.now().plus(VERIFICATION_BLOCK_MINUTES, ChronoUnit.MINUTES));
        }

        linkRepository.save(link);

        auditService.recordAudit(link.getId(), AuditAction.VERIFICATION_ATTEMPT, "FAILED",
                "Invalid OTP; attempts=" + attempts);
        auditService.recordInteraction(link.getId(), InteractionType.VERIFICATION, "FAILED",
                "Invalid verification code");

        throw new IllegalArgumentException("Invalid verification code");
    }

    private void revokeCredential(WhatsAppLinkEntity link) {
        sessionClient.revokeCredential(link.getSessionCredentialId());
        link.setSessionCredentialId(null);
    }

    private WhatsAppLinkEntity getLinkOrThrow(String linkId) {
        return linkRepository.findById(linkId)
                .orElseThrow(() -> new LinkNotFoundException(linkId));
    }

    private boolean isOtpValid(String otpCode) {
        return OTP_MVP_CODE.equals(otpCode);
    }

    private String maskPhone(String phone) {
        if (phone == null || phone.length() < 4) {
            return "****";
        }
        return phone.substring(0, Math.min(4, phone.length())) + "****"
                + phone.substring(phone.length() - 4);
    }

    private String maskEmail(String email) {
        if (email == null || !email.contains("@")) {
            return "****";
        }
        String[] parts = email.split("@", 2);
        String local = parts[0];
        String maskedLocal = local.length() <= 1 ? "*" : local.charAt(0) + "***";
        return maskedLocal + "@" + parts[1];
    }
}
