package com.bank.users.audit.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.bank.users.audit.entity.AuditLog;

/** Persistencia interna de auditoría, sin exposición HTTP. */
public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {
}
