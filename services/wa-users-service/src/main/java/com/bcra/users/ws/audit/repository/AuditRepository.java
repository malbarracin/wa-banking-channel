package com.bcra.users.ws.audit.repository;

import java.util.List;
import java.util.UUID;

import com.bcra.users.ws.audit.entity.AuditEntry;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditRepository extends JpaRepository<AuditEntry, UUID> {
    List<AuditEntry> findByUserIdOrderByOccurredAtAsc(UUID userId);
}
