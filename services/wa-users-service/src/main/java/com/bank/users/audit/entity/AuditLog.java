package com.bank.users.audit.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Registro independiente de la entidad User; no conserva valores personales modificados. */
@Entity
@Table(name = "audit_logs")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, updatable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 32)
    private AuditAction action;

    @Column(nullable = false, updatable = false, length = 16)
    private String result;

    @Column(nullable = false, updatable = false, length = 255)
    private String detail;

    @Column(nullable = false, updatable = false)
    private Instant occurredAt;

    @Builder
    private AuditLog(UUID userId, AuditAction action, String result, String detail, Instant occurredAt) {
        this.userId = userId;
        this.action = action;
        this.result = result;
        this.detail = detail;
        this.occurredAt = occurredAt;
    }
}
