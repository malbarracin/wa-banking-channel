package com.bcra.channel.ws.binding.service;

import java.time.Clock;
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
import com.bcra.channel.ws.binding.entity.Binding;
import com.bcra.channel.ws.binding.entity.BindingStatus;
import com.bcra.channel.ws.binding.exception.BindingConflictException;
import com.bcra.channel.ws.binding.mapper.BindingMapper;
import com.bcra.channel.ws.binding.repository.BindingRepository;
import com.bcra.channel.ws.config.ChannelMessages;
import com.bcra.channel.ws.exception.BadRequestException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Casos de uso del vínculo: consulta de estado y aceptación de términos.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BindingService {

    public static final String ACTOR_CLIENTE = "CLIENTE";
    public static final String ACTOR_SISTEMA = "SISTEMA";

    private static final String TERMS_ACCEPTED_MESSAGE =
            "Términos aceptados. Podés continuar con la verificación de identidad.";

    private final BindingRepository bindingRepository;
    private final BindingMapper bindingMapper;
    private final BindingStateMachine stateMachine;
    private final AuditService auditService;
    private final ChannelMessages channelMessages;
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
            throw new BindingConflictException("El número ya tiene un vínculo activo");
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
