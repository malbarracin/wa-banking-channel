package com.bcra.users.ws.audit.service;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.bcra.users.ws.audit.entity.AuditAction;
import com.bcra.users.ws.audit.entity.AuditEntry;
import com.bcra.users.ws.audit.entity.AuditResult;
import com.bcra.users.ws.audit.repository.AuditRepository;
import com.bcra.users.ws.user.entity.UserStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Registra operaciones sin almacenar valores de datos personales. */
@Service
public class AuditService {
    private static final Set<String> ALLOWED_FIELDS = Set.of("displayName", "contactEmail", "contactPhone");
    private final AuditRepository repository;

    public AuditService(AuditRepository repository) {
        this.repository = repository;
    }

    /** Registra un alta en la transacción del llamador. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void registerCreation(UUID userId, String actor, AuditResult result, Instant occurredAt) {
        repository.save(new AuditEntry(userId, AuditAction.ALTA, result, null, null, null, actor, occurredAt));
    }

    /** Registra únicamente los nombres de los campos modificados. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void registerModification(UUID userId, String actor, AuditResult result, Instant occurredAt,
            Set<String> changedFields) {
        if (changedFields.isEmpty() || !ALLOWED_FIELDS.containsAll(changedFields)) {
            throw new IllegalArgumentException("Campos de auditoría inválidos");
        }
        String fields = String.join(",", changedFields.stream().sorted().toList());
        repository.save(new AuditEntry(userId, AuditAction.MODIFICACION, result, null, null, fields, actor, occurredAt));
    }

    /** Registra una transición de estado. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void registerStatusChange(UUID userId, String actor, AuditResult result, Instant occurredAt,
            UserStatus previousStatus, UserStatus newStatus) {
        repository.save(new AuditEntry(userId, AuditAction.CAMBIO_ESTADO, result, previousStatus, newStatus,
                null, actor, occurredAt));
    }

    /** Devuelve el historial cronológico de un cliente. */
    @Transactional(readOnly = true)
    public List<AuditEntry> history(UUID userId) {
        return repository.findByUserIdOrderByOccurredAtAsc(userId);
    }
}
