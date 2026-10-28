package com.bank.users.audit.service;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

import com.bank.users.audit.entity.AuditAction;
import com.bank.users.audit.entity.AuditLog;
import com.bank.users.audit.repository.AuditLogRepository;

import lombok.RequiredArgsConstructor;

/** Registra operaciones exitosas dentro de la transacción del caso de uso que las aplica. */
@Service
@Transactional(propagation = Propagation.MANDATORY)
@RequiredArgsConstructor
public class AuditService {

    private static final String SUCCESS = "SUCCESS";
    private static final Set<String> UPDATABLE_FIELDS = Set.of("displayName", "email", "phone", "preferences");
    private static final Set<String> STATUSES = Set.of("ACTIVE", "SUSPENDED", "LOGICALLY_DELETED");

    private final AuditLogRepository auditLogRepository;

    /** Registra un alta confirmada por el caso de uso, sin copiar datos del usuario. */
    public void recordUserCreated(UUID userId) {
        record(userId, AuditAction.USER_CREATED, "status=ACTIVE");
    }

    /** Recibe solo nombres de campos permitidos, nunca sus valores ni claves de preferencias. */
    public void recordUserUpdated(UUID userId, Set<String> changedFields) {
        Assert.notEmpty(changedFields, "Debe indicar los campos modificados");
        Assert.isTrue(UPDATABLE_FIELDS.containsAll(changedFields), "Campo de auditoría no permitido");
        String detail = changedFields.stream().sorted().collect(Collectors.joining(",", "fields=", ""));
        record(userId, AuditAction.USER_UPDATED, detail);
    }

    /** Recibe el nombre del estado resultante, sin dependencia del modelo User. */
    public void recordStatusChanged(UUID userId, String resultingStatus) {
        Assert.hasText(resultingStatus, "Debe indicar el estado resultante");
        Assert.isTrue(STATUSES.contains(resultingStatus), "Estado de auditoría no permitido");
        record(userId, AuditAction.STATUS_CHANGED, "status=" + resultingStatus);
    }

    private void record(UUID userId, AuditAction action, String detail) {
        Assert.notNull(userId, "Debe indicar el identificador del usuario");
        auditLogRepository.save(AuditLog.builder()
                .userId(userId)
                .action(action)
                .result(SUCCESS)
                .detail(detail)
                .occurredAt(Instant.now())
                .build());
    }
}
