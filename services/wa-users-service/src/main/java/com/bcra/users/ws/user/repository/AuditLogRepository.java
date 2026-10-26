package com.bcra.users.ws.user.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.bcra.users.ws.user.entity.AuditLog;

/** Persistencia de las operaciones auditadas del usuario. */
public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {
}
