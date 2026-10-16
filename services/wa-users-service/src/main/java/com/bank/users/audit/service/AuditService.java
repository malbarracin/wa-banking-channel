package com.bank.users.audit.service;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.bank.users.audit.entity.AuditAction;
import com.bank.users.audit.entity.AuditDetail;
import com.bank.users.audit.entity.AuditEntry;
import com.bank.users.audit.entity.AuditResult;
import com.bank.users.audit.repository.AuditEntryRepository;

import lombok.RequiredArgsConstructor;

/** Auditoría reutilizable. Invocar mediante el bean Spring para aplicar las transacciones. */
@Service
@RequiredArgsConstructor
public class AuditService {

    private static final int HISTORY_LIMIT = 20;

    private final AuditEntryRepository repository;

    /** Participa obligatoriamente del commit del caso de uso; su rollback también elimina el OK. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void recordSuccess(UUID userId, AuditAction action) {
        repository.append(new AuditEntry(Objects.requireNonNull(userId, "userId es obligatorio"),
                action, AuditResult.OK, AuditDetail.OPERACION_CONFIRMADA));
    }

    /** Confirma el rechazo independientemente del rollback llamador; userId puede ser null en altas fallidas. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordRejection(UUID userId, AuditAction action, AuditDetail detail) {
        if (detail == null || detail == AuditDetail.OPERACION_CONFIRMADA) {
            throw new IllegalArgumentException("El rechazo requiere un motivo de rechazo");
        }
        repository.append(new AuditEntry(userId, action, AuditResult.RECHAZADO, detail));
    }

    /** Devuelve hasta 20 registros recientes, sin información personal ni datos de canal. */
    @Transactional(readOnly = true)
    public List<AuditEntry> findHistory(UUID userId) {
        return repository.findRecentByUserId(Objects.requireNonNull(userId, "userId es obligatorio"), HISTORY_LIMIT);
    }
}
