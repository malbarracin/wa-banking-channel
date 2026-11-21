package com.bcra.users.ws.audit.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.repository.Repository;

import com.bcra.users.ws.audit.entity.AuditEvent;

public interface AuditEventRepository extends Repository<AuditEvent, Long> {
    AuditEvent save(AuditEvent event);

    List<AuditEvent> findByUserIdOrderByCreatedAtDesc(UUID userId);
}
