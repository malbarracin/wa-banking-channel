package com.bcra.users.ws.audit.entity;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Immutable
@Table(name = "audit_events")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AuditEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private AuditAction action;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private AuditResult resultado;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, updatable = false, columnDefinition = "jsonb")
    private List<String> detalle;
    @Column(name = "correlation_id", nullable = false, updatable = false)
    private String correlationId;
    @Column(nullable = false, updatable = false)
    private String actor;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public AuditEvent(UUID userId, AuditAction action, AuditResult resultado, List<String> detalle,
            String correlationId, String actor) {
        this.userId = userId;
        this.action = action;
        this.resultado = resultado;
        this.detalle = List.copyOf(detalle);
        this.correlationId = correlationId;
        this.actor = actor;
        this.createdAt = Instant.now();
    }
}
