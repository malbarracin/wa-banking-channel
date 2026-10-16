package com.bank.users.audit.repository;

import java.util.List;
import java.util.UUID;

import jakarta.persistence.EntityManager;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.bank.users.audit.entity.AuditEntry;

import lombok.RequiredArgsConstructor;

/** Acceso limitado a inserción y lectura; no ofrece merge, modificación ni borrado. */
@Repository
@RequiredArgsConstructor
public class AuditEntryRepository {

    private final EntityManager entityManager;

    @Transactional(propagation = Propagation.MANDATORY)
    public void append(AuditEntry entry) {
        entityManager.persist(entry);
    }

    @Transactional(readOnly = true)
    public List<AuditEntry> findRecentByUserId(UUID userId, int limit) {
        return List.copyOf(entityManager.createQuery("""
                select entry from AuditEntry entry where entry.userId = :userId
                order by entry.occurredAt desc, entry.id desc
                """, AuditEntry.class)
                .setParameter("userId", userId)
                .setMaxResults(limit)
                .getResultList());
    }
}
