package com.bcra.channel.ws.binding.service;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bcra.channel.ws.audit.entity.AuditAction;
import com.bcra.channel.ws.audit.service.AuditService;
import com.bcra.channel.ws.binding.dto.BindingLifecycleActor;
import com.bcra.channel.ws.binding.dto.BlockBindingRequest;
import com.bcra.channel.ws.binding.dto.BlockBindingResponse;
import com.bcra.channel.ws.binding.dto.UnbindRequest;
import com.bcra.channel.ws.binding.dto.UnbindResponse;
import com.bcra.channel.ws.binding.entity.Binding;
import com.bcra.channel.ws.binding.entity.BindingStatus;
import com.bcra.channel.ws.binding.exception.BindingConflictException;
import com.bcra.channel.ws.binding.exception.BindingNotFoundException;
import com.bcra.channel.ws.binding.repository.BindingRepository;
import com.bcra.channel.ws.config.BankInternalAuthFilter;
import com.bcra.channel.ws.config.ChannelMessages;
import com.bcra.channel.ws.config.RequireInternalRole.InternalRole;
import com.bcra.channel.ws.exception.BadRequestException;
import com.bcra.channel.ws.exception.ForbiddenException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Bloqueo y desvinculación del vínculo (cliente o política del banco).
 * El corte es inmediato en la misma transacción; la respuesta indica revocación de credencial.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BindingLifecycleService {

    private static final boolean CREDENTIAL_REVOCATION_REQUIRED = true;

    private final BindingRepository bindingRepository;
    private final BindingStateMachine stateMachine;
    private final AuditService auditService;
    private final ChannelMessages channelMessages;
    private final BankInternalAuthFilter bankInternalAuthFilter;
    private final Clock clock;

    /**
     * Bloquea un vínculo activo a pedido del cliente o del banco.
     *
     * @param whatsappNumber       número en E.164
     * @param request              actor, motivo y confirmación
     * @param internalRoleHeader   valor de {@code X-Internal-Role} (obligatorio si actor=BANCO)
     * @return vínculo en {@link BindingStatus#BLOQUEADO} con indicador de revocación
     */
    @Transactional
    public BlockBindingResponse block(
            String whatsappNumber, BlockBindingRequest request, String internalRoleHeader) {
        Objects.requireNonNull(request, "request");
        WhatsappNumberFormat.requireValid(whatsappNumber);
        authorizeAndConfirm(request.actor(), request.confirmed(), internalRoleHeader);

        Instant now = Instant.now(clock);
        Binding binding = requireBindingForUpdate(whatsappNumber);
        BindingStatus current = binding.getStatus();
        if (current == BindingStatus.BLOQUEADO || current == BindingStatus.DESVINCULADO) {
            throw new BindingConflictException(
                    "El vínculo no puede bloquearse desde estado " + current);
        }
        if (current != BindingStatus.ACTIVO) {
            throw new BindingConflictException(
                    "El vínculo no puede bloquearse desde estado " + current);
        }
        stateMachine.assertTransition(current, BindingStatus.BLOQUEADO);

        String actorName = request.actor().name();
        binding.setStatus(BindingStatus.BLOQUEADO);
        binding.setActor(actorName);
        binding.setUpdatedAt(now);
        Binding saved = bindingRepository.save(binding);

        auditService.record(
                saved.getId(),
                whatsappNumber,
                AuditAction.BINDING_BLOCKED,
                AuditService.RESULT_OK,
                actorName,
                auditDetail(BindingStatus.BLOQUEADO, request.reason()));
        log.info("Vínculo bloqueado: bindingId={}, actor={}", saved.getId(), actorName);

        return new BlockBindingResponse(
                saved.getId(),
                BindingStatus.BLOQUEADO,
                channelMessages.blocked(),
                CREDENTIAL_REVOCATION_REQUIRED);
    }

    /**
     * Desvincula un número desde {@link BindingStatus#ACTIVO} o {@link BindingStatus#BLOQUEADO}.
     *
     * @param whatsappNumber       número en E.164
     * @param request              actor, motivo y confirmación
     * @param internalRoleHeader   valor de {@code X-Internal-Role} (obligatorio si actor=BANCO)
     * @return vínculo en {@link BindingStatus#DESVINCULADO} con indicador de revocación
     */
    @Transactional
    public UnbindResponse unbind(
            String whatsappNumber, UnbindRequest request, String internalRoleHeader) {
        Objects.requireNonNull(request, "request");
        WhatsappNumberFormat.requireValid(whatsappNumber);
        authorizeAndConfirm(request.actor(), request.confirmed(), internalRoleHeader);

        Instant now = Instant.now(clock);
        Binding binding = requireBindingForUpdate(whatsappNumber);
        BindingStatus current = binding.getStatus();
        if (current == BindingStatus.DESVINCULADO) {
            throw new BindingConflictException("El vínculo ya está desvinculado");
        }
        stateMachine.assertTransition(current, BindingStatus.DESVINCULADO);

        String actorName = request.actor().name();
        binding.setStatus(BindingStatus.DESVINCULADO);
        binding.setActor(actorName);
        binding.setUpdatedAt(now);
        Binding saved = bindingRepository.save(binding);

        auditService.record(
                saved.getId(),
                whatsappNumber,
                AuditAction.BINDING_UNBOUND,
                AuditService.RESULT_OK,
                actorName,
                auditDetail(BindingStatus.DESVINCULADO, request.reason()));
        log.info("Vínculo desvinculado: bindingId={}, actor={}", saved.getId(), actorName);

        return new UnbindResponse(
                saved.getId(),
                BindingStatus.DESVINCULADO,
                channelMessages.unbound(),
                CREDENTIAL_REVOCATION_REQUIRED);
    }

    private Binding requireBindingForUpdate(String whatsappNumber) {
        return bindingRepository.findByWhatsappNumberForUpdate(whatsappNumber)
                .orElseThrow(BindingNotFoundException::new);
    }

    private void authorizeAndConfirm(
            BindingLifecycleActor actor, Boolean confirmed, String internalRoleHeader) {
        Objects.requireNonNull(actor, "actor");
        if (actor == BindingLifecycleActor.CLIENTE) {
            if (!Boolean.TRUE.equals(confirmed)) {
                throw new BadRequestException(
                        "La confirmación explícita es obligatoria para el cliente");
            }
            return;
        }
        assertBankPolicyAuthorized(internalRoleHeader);
    }

    private void assertBankPolicyAuthorized(String internalRoleHeader) {
        if (internalRoleHeader == null || internalRoleHeader.isBlank()) {
            throw new ForbiddenException("Rol interno no autorizado");
        }
        String expected = bankInternalAuthFilter.resolveExpectedRole(InternalRole.BANK_POLICY);
        if (!expected.equals(internalRoleHeader)) {
            throw new ForbiddenException("Rol interno no autorizado");
        }
    }

    private static String auditDetail(BindingStatus resultingStatus, String reason) {
        return "Estado " + resultingStatus + "; motivo=" + reason;
    }
}
