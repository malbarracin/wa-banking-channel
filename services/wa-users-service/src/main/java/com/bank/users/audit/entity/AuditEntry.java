package com.bank.users.audit.entity;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import org.hibernate.annotations.Immutable;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Registro inmutable sin relación FK: un rechazo puede referir a un usuario no confirmado. */
@Entity
@Table(name = "audit_entries", indexes = @Index(name = "idx_audit_user_time", columnList = "user_id,occurred_at"))
@Immutable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AuditEntry {

    @Id
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Column(name = "user_id", updatable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(updatable = false, nullable = false)
    private AuditAction action;

    @Enumerated(EnumType.STRING)
    @Column(updatable = false, nullable = false)
    private AuditResult result;

    @Column(updatable = false, nullable = false)
    private String detail;

    @Column(name = "occurred_at", updatable = false, nullable = false)
    private Instant occurredAt;

    /** Crea exclusivamente registros nuevos con un motivo libre de texto externo. */
    public AuditEntry(UUID userId, AuditAction action, AuditResult result, AuditDetail detail) {
        this.id = UUID.randomUUID();
        this.userId = userId;
        this.action = Objects.requireNonNull(action, "action es obligatorio");
        this.result = Objects.requireNonNull(result, "result es obligatorio");
        this.detail = Objects.requireNonNull(detail, "detail es obligatorio").name();
        this.occurredAt = Instant.now();
    }
}
