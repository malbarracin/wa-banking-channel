package com.bcra.channel.ws.binding.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bcra.channel.ws.audit.entity.AuditAction;
import com.bcra.channel.ws.audit.service.AuditService;
import com.bcra.channel.ws.binding.dto.AcceptTermsRequest;
import com.bcra.channel.ws.binding.dto.AcceptTermsResponse;
import com.bcra.channel.ws.binding.dto.BindingStatusResponse;
import com.bcra.channel.ws.binding.dto.VerifiableResultResponse;
import com.bcra.channel.ws.binding.dto.VerifyIdentityRequest;
import com.bcra.channel.ws.binding.dto.VerifyIdentityResponse;
import com.bcra.channel.ws.binding.entity.Binding;
import com.bcra.channel.ws.binding.entity.BindingStatus;
import com.bcra.channel.ws.binding.entity.VerificationAttempt;
import com.bcra.channel.ws.binding.exception.BindingConflictException;
import com.bcra.channel.ws.binding.exception.ClientNotFoundException;
import com.bcra.channel.ws.binding.mapper.BindingMapper;
import com.bcra.channel.ws.binding.repository.BindingRepository;
import com.bcra.channel.ws.binding.repository.VerificationAttemptRepository;
import com.bcra.channel.ws.client.UserLookupResult;
import com.bcra.channel.ws.client.UserLookupStatus;
import com.bcra.channel.ws.client.UsersServiceClient;
import com.bcra.channel.ws.config.BankChannelProperties;
import com.bcra.channel.ws.config.ChannelMessages;
import com.bcra.channel.ws.exception.BadRequestException;
import com.bcra.channel.ws.exception.VerificationLockedException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Casos de uso del vínculo: estado, términos y verificación de identidad.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BindingService {

    public static final String ACTOR_CLIENTE = "CLIENTE";
    public static final String ACTOR_SISTEMA = "SISTEMA";

    private static final String TERMS_ACCEPTED_MESSAGE =
            "Términos aceptados. Podés continuar con la verificación de identidad.";
    private static final String TERMS_REQUIRED_MESSAGE =
            "Debés aceptar los términos y condiciones antes de verificar la identidad.";

    private final BindingRepository bindingRepository;
    private final VerificationAttemptRepository verificationAttemptRepository;
    private final BindingMapper bindingMapper;
    private final BindingStateMachine stateMachine;
    private final AuditService auditService;
    private final ChannelMessages channelMessages;
    private final BankChannelProperties channelProperties;
    private final UsersServiceClient usersServiceClient;
    private final IdentityVerifierResolver identityVerifierResolver;
    private final Clock clock;

    /**
     * Consulta el estado del vínculo de un número.
     *
     * @param whatsappNumber número en E.164
     * @return estado actual; {@link BindingStatus#SIN_VINCULO} si no hay registro
     */
    @Transactional
    public BindingStatusResponse getStatus(String whatsappNumber) {
        WhatsappNumberFormat.requireValid(whatsappNumber);

        Optional<Binding> existing = bindingRepository.findByWhatsappNumber(whatsappNumber);
        if (existing.isEmpty()) {
            auditService.record(
                    AuditService.NO_BINDING_ID,
                    whatsappNumber,
                    AuditAction.STATUS_QUERIED,
                    AuditService.RESULT_OK,
                    ACTOR_SISTEMA,
                    "Sin vínculo registrado");
            return bindingMapper.unboundStatus(whatsappNumber);
        }

        Binding binding = existing.get();
        auditService.record(
                binding.getId(),
                whatsappNumber,
                AuditAction.STATUS_QUERIED,
                AuditService.RESULT_OK,
                ACTOR_SISTEMA,
                "Estado " + binding.getStatus());
        return bindingMapper.toStatusResponse(binding);
    }

    /**
     * Registra la aceptación o el rechazo de términos y condiciones.
     *
     * @param whatsappNumber número en E.164
     * @param request        aceptación explícita
     * @return vínculo en {@link BindingStatus#PENDIENTE_VERIFICACION}
     */
    @Transactional
    public AcceptTermsResponse acceptTerms(String whatsappNumber, AcceptTermsRequest request) {
        Objects.requireNonNull(request, "request");
        WhatsappNumberFormat.requireValid(whatsappNumber);

        if (!Boolean.TRUE.equals(request.accepted())) {
            auditService.recordTermsRejected(whatsappNumber);
            throw new BadRequestException(channelMessages.termsRejected());
        }

        Instant now = Instant.now(clock);
        Binding binding = bindingRepository.findByWhatsappNumberForUpdate(whatsappNumber)
                .map(existing -> applyTermsAcceptance(existing, now))
                .orElseGet(() -> createPendingBinding(whatsappNumber, now));

        Binding saved = bindingRepository.save(binding);
        auditService.record(
                saved.getId(),
                whatsappNumber,
                AuditAction.TERMS_ACCEPTED,
                AuditService.RESULT_OK,
                ACTOR_CLIENTE,
                "Términos aceptados");
        log.info("Términos aceptados: bindingId={}, status={}", saved.getId(), saved.getStatus());

        return new AcceptTermsResponse(saved.getId(), saved.getStatus(), TERMS_ACCEPTED_MESSAGE);
    }

    /**
     * Verifica la identidad, aplica anti-fraude y activa el vínculo si corresponde.
     * {@code verificationData} no se persiste, audita ni registra en logs.
     * Los conflictos y el bloqueo por intentos no revierten la auditoría ni el estado ya persistidos.
     *
     * @param whatsappNumber número en E.164
     * @param request        clientId interno de H1 y dato de verificación
     * @return resultado del intento (activo con resultado verificable, o pendiente con intentos restantes)
     */
    @Transactional(noRollbackFor = {
            VerificationLockedException.class,
            BindingConflictException.class,
            ClientNotFoundException.class
    })
    public VerifyIdentityResponse verify(String whatsappNumber, VerifyIdentityRequest request) {
        Objects.requireNonNull(request, "request");
        WhatsappNumberFormat.requireValid(whatsappNumber);

        Binding binding = bindingRepository.findByWhatsappNumberForUpdate(whatsappNumber)
                .orElseThrow(() -> new BindingConflictException(TERMS_REQUIRED_MESSAGE));

        if (binding.getStatus() == BindingStatus.ACTIVO
                && !Objects.equals(binding.getClientId(), request.clientId())) {
            auditService.record(
                    binding.getId(),
                    whatsappNumber,
                    AuditAction.ANTIFRAUD_CHECK,
                    AuditService.RESULT_ERROR,
                    ACTOR_SISTEMA,
                    "Conflicto: número ACTIVO con otro cliente; escalamiento a soporte");
            throw new BindingConflictException(channelMessages.numberAlreadyLinked());
        }

        if (binding.getStatus() != BindingStatus.PENDIENTE_VERIFICACION) {
            throw new BindingConflictException(TERMS_REQUIRED_MESSAGE);
        }

        Instant now = Instant.now(clock);
        int maxAttempts = channelProperties.getVerification().getMaxAttempts();
        Instant windowStart = now.minus(Duration.ofMinutes(channelProperties.getVerification().getWindowMinutes()));
        long failedInWindow = verificationAttemptRepository
                .countByBindingIdAndSuccessFalseAndAttemptedAtGreaterThanEqual(binding.getId(), windowStart);

        if (failedInWindow >= maxAttempts) {
            lockBinding(binding, now, whatsappNumber);
            throw new VerificationLockedException(channelMessages.verificationLocked());
        }

        UserLookupResult lookup = usersServiceClient.findById(request.clientId());
        auditService.record(
                binding.getId(),
                whatsappNumber,
                AuditAction.CLIENT_LOOKUP,
                lookup.status() == UserLookupStatus.FOUND_ACTIVE
                        ? AuditService.RESULT_OK
                        : AuditService.RESULT_ERROR,
                ACTOR_SISTEMA,
                "Lookup " + lookup.status());

        if (lookup.status() == UserLookupStatus.NOT_FOUND) {
            throw new ClientNotFoundException(channelMessages.clientNotFound());
        }
        if (lookup.status() == UserLookupStatus.FOUND_SUSPENDED
                || lookup.status() == UserLookupStatus.FOUND_INACTIVE) {
            throw new BindingConflictException(channelMessages.clientSuspended());
        }

        boolean identityOk = identityVerifierResolver.verify(lookup, request.verificationData());
        if (!identityOk) {
            return handleFailedVerification(binding, whatsappNumber, now, failedInWindow, maxAttempts);
        }

        if (bindingRepository.existsByWhatsappNumberAndStatusAndClientIdNot(
                whatsappNumber, BindingStatus.ACTIVO, request.clientId())) {
            auditService.record(
                    binding.getId(),
                    whatsappNumber,
                    AuditAction.ANTIFRAUD_CHECK,
                    AuditService.RESULT_ERROR,
                    ACTOR_SISTEMA,
                    "Conflicto: número ACTIVO con otro cliente; escalamiento a soporte");
            throw new BindingConflictException(channelMessages.numberAlreadyLinked());
        }

        stateMachine.assertTransition(BindingStatus.PENDIENTE_VERIFICACION, BindingStatus.ACTIVO);
        binding.setClientId(request.clientId());
        binding.setStatus(BindingStatus.ACTIVO);
        binding.setVerifiedAt(now);
        binding.setActor(ACTOR_CLIENTE);
        binding.setUpdatedAt(now);
        Binding saved = bindingRepository.save(binding);

        recordSuccessfulAttempt(saved.getId(), now);

        auditService.record(
                saved.getId(),
                whatsappNumber,
                AuditAction.IDENTITY_VERIFIED,
                AuditService.RESULT_OK,
                ACTOR_CLIENTE,
                "Identidad verificada");
        auditService.record(
                saved.getId(),
                whatsappNumber,
                AuditAction.ANTIFRAUD_CHECK,
                AuditService.RESULT_OK,
                ACTOR_SISTEMA,
                "Sin conflicto de número");
        auditService.record(
                saved.getId(),
                whatsappNumber,
                AuditAction.BINDING_ACTIVATED,
                AuditService.RESULT_OK,
                ACTOR_SISTEMA,
                "Vínculo activado");
        auditService.record(
                saved.getId(),
                whatsappNumber,
                AuditAction.VERIFIABLE_RESULT_ISSUED,
                AuditService.RESULT_OK,
                ACTOR_SISTEMA,
                "Resultado verificable emitido");

        VerifiableResultResponse verifiableResult = bindingMapper.toVerifiableResult(saved);
        String welcome = channelMessages.welcome(displayNameOrFallback(lookup));
        log.info("Vínculo activado: bindingId={}, clientId={}", saved.getId(), saved.getClientId());

        return new VerifyIdentityResponse(
                saved.getId(),
                saved.getStatus(),
                0,
                verifiableResult,
                welcome);
    }

    private VerifyIdentityResponse handleFailedVerification(
            Binding binding,
            String whatsappNumber,
            Instant now,
            long failedInWindow,
            int maxAttempts) {
        recordFailedAttempt(binding.getId(), now);
        long failedAfter = failedInWindow + 1;
        int remaining = (int) Math.max(0, maxAttempts - failedAfter);

        auditService.record(
                binding.getId(),
                whatsappNumber,
                AuditAction.IDENTITY_FAILED,
                AuditService.RESULT_ERROR,
                ACTOR_CLIENTE,
                "Verificación fallida; restantes=" + remaining);

        if (failedAfter >= maxAttempts) {
            lockBinding(binding, now, whatsappNumber);
            throw new VerificationLockedException(channelMessages.verificationLocked());
        }

        binding.setUpdatedAt(now);
        bindingRepository.save(binding);
        log.info("Verificación fallida: bindingId={}, attemptsRemaining={}", binding.getId(), remaining);

        return new VerifyIdentityResponse(
                binding.getId(),
                binding.getStatus(),
                remaining,
                null,
                channelMessages.verificationFailed(remaining));
    }

    private void lockBinding(Binding binding, Instant now, String whatsappNumber) {
        stateMachine.assertTransition(binding.getStatus(), BindingStatus.BLOQUEADO);
        binding.setStatus(BindingStatus.BLOQUEADO);
        binding.setActor(ACTOR_SISTEMA);
        binding.setUpdatedAt(now);
        bindingRepository.save(binding);
        auditService.record(
                binding.getId(),
                whatsappNumber,
                AuditAction.VERIFICATION_LOCKED,
                AuditService.RESULT_BLOQUEADO,
                ACTOR_SISTEMA,
                "Tope de intentos agotado");
        log.info("Vínculo bloqueado por intentos: bindingId={}", binding.getId());
    }

    private void recordFailedAttempt(UUID bindingId, Instant attemptedAt) {
        VerificationAttempt attempt = new VerificationAttempt();
        attempt.setId(UUID.randomUUID());
        attempt.setBindingId(bindingId);
        attempt.setAttemptedAt(attemptedAt);
        attempt.setSuccess(false);
        verificationAttemptRepository.save(attempt);
    }

    private void recordSuccessfulAttempt(UUID bindingId, Instant attemptedAt) {
        VerificationAttempt attempt = new VerificationAttempt();
        attempt.setId(UUID.randomUUID());
        attempt.setBindingId(bindingId);
        attempt.setAttemptedAt(attemptedAt);
        attempt.setSuccess(true);
        verificationAttemptRepository.save(attempt);
    }

    private static String displayNameOrFallback(UserLookupResult lookup) {
        String name = lookup.displayName();
        if (name == null || name.isBlank()) {
            return "cliente";
        }
        return name.trim();
    }

    private Binding createPendingBinding(String whatsappNumber, Instant now) {
        stateMachine.assertTransition(BindingStatus.SIN_VINCULO, BindingStatus.PENDIENTE_VERIFICACION);

        Binding binding = new Binding();
        binding.setId(UUID.randomUUID());
        binding.setWhatsappNumber(whatsappNumber);
        binding.setClientId(null);
        binding.setStatus(BindingStatus.PENDIENTE_VERIFICACION);
        binding.setTermsAcceptedAt(now);
        binding.setVerifiedAt(null);
        binding.setLanguagePreference("es");
        binding.setNotificationsEnabled(true);
        binding.setActor(ACTOR_CLIENTE);
        binding.setCreatedAt(now);
        binding.setUpdatedAt(now);
        return binding;
    }

    private Binding applyTermsAcceptance(Binding existing, Instant now) {
        if (existing.getStatus() == BindingStatus.ACTIVO) {
            throw new BindingConflictException(channelMessages.numberAlreadyLinked());
        }
        stateMachine.assertTransition(existing.getStatus(), BindingStatus.PENDIENTE_VERIFICACION);

        existing.setStatus(BindingStatus.PENDIENTE_VERIFICACION);
        existing.setTermsAcceptedAt(now);
        existing.setClientId(null);
        existing.setVerifiedAt(null);
        existing.setActor(ACTOR_CLIENTE);
        existing.setUpdatedAt(now);
        return existing;
    }
}
