package com.bcra.channel.ws.audit.service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.bcra.channel.ws.audit.dto.AuditEntryResponse;
import com.bcra.channel.ws.audit.entity.AuditAction;
import com.bcra.channel.ws.audit.entity.AuditEntry;
import com.bcra.channel.ws.audit.exception.AuditHistoryNotFoundException;
import com.bcra.channel.ws.audit.mapper.AuditMapper;
import com.bcra.channel.ws.audit.repository.AuditRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Registro y consulta del historial de auditoría del canal.
 * La API de registro no acepta valores de verificación ni secretos.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditService {

    public static final String RESULT_OK = "OK";
    public static final String RESULT_ERROR = "ERROR";
    public static final String RESULT_BLOQUEADO = "BLOQUEADO";

    private final AuditRepository auditRepository;
    private final AuditMapper auditMapper;
    private final Clock clock;

    /** Identificador de auditoría cuando aún no hay registro de vínculo. */
    public static final UUID NO_BINDING_ID = new UUID(0L, 0L);

    public static final String ACTOR_CLIENTE = "CLIENTE";

    /**
     * Persiste una entrada de auditoría.
     *
     * @param bindingId      identificador del vínculo
     * @param whatsappNumber número WhatsApp (E.164)
     * @param action         acción auditada
     * @param result         resultado ({@link #RESULT_OK}, {@link #RESULT_ERROR} o {@link #RESULT_BLOQUEADO})
     * @param actor          quién disparó la acción
     * @param detail         detalle opcional sin secretos ni datos de verificación
     * @return entrada persistida
     */
    @Transactional
    public AuditEntry record(
            UUID bindingId,
            String whatsappNumber,
            AuditAction action,
            String result,
            String actor,
            String detail) {
        Objects.requireNonNull(bindingId, "bindingId");
        Objects.requireNonNull(whatsappNumber, "whatsappNumber");
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(result, "result");
        Objects.requireNonNull(actor, "actor");

        AuditEntry entry = new AuditEntry();
        entry.setId(UUID.randomUUID());
        entry.setBindingId(bindingId);
        entry.setWhatsappNumber(whatsappNumber);
        entry.setAction(action);
        entry.setResult(result);
        entry.setActor(actor);
        entry.setDetail(detail);
        entry.setOccurredAt(Instant.now(clock));

        AuditEntry saved = auditRepository.save(entry);
        log.info("Auditoría registrada: action={}, result={}, actor={}, id={}",
                saved.getAction(), saved.getResult(), saved.getActor(), saved.getId());
        return saved;
    }

    /**
     * Audita el rechazo de términos sin crear vínculo.
     * Transacción propia: el registro queda aunque el caso de uso aborte con 400.
     *
     * @param whatsappNumber número WhatsApp (E.164)
     * @return entrada persistida
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public AuditEntry recordTermsRejected(String whatsappNumber) {
        Objects.requireNonNull(whatsappNumber, "whatsappNumber");

        AuditEntry entry = new AuditEntry();
        entry.setId(UUID.randomUUID());
        entry.setBindingId(NO_BINDING_ID);
        entry.setWhatsappNumber(whatsappNumber);
        entry.setAction(AuditAction.TERMS_REJECTED);
        entry.setResult(RESULT_ERROR);
        entry.setActor(ACTOR_CLIENTE);
        entry.setDetail("Términos rechazados");
        entry.setOccurredAt(Instant.now(clock));

        AuditEntry saved = auditRepository.save(entry);
        log.info("Auditoría registrada: action={}, result={}, actor={}, id={}",
                saved.getAction(), saved.getResult(), saved.getActor(), saved.getId());
        return saved;
    }

    /**
     * Historial de un número ordenado por fecha descendente.
     *
     * @param whatsappNumber número WhatsApp (E.164)
     * @return entradas de auditoría
     * @throws AuditHistoryNotFoundException si no hay registros
     */
    @Transactional(readOnly = true)
    public List<AuditEntryResponse> findHistoryByWhatsappNumber(String whatsappNumber) {
        Objects.requireNonNull(whatsappNumber, "whatsappNumber");
        List<AuditEntry> entries = auditRepository.findByWhatsappNumberOrderByOccurredAtDesc(whatsappNumber);
        if (entries.isEmpty()) {
            throw new AuditHistoryNotFoundException();
        }
        return entries.stream().map(auditMapper::toResponse).toList();
    }
}
