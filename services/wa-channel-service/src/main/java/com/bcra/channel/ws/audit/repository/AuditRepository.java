package com.bcra.channel.ws.audit.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.bcra.channel.ws.audit.entity.AuditEntry;

/**
 * Persistencia de entradas de auditoría del canal.
 */
public interface AuditRepository extends JpaRepository<AuditEntry, UUID> {

    List<AuditEntry> findByWhatsappNumberOrderByOccurredAtDesc(String whatsappNumber);
}
