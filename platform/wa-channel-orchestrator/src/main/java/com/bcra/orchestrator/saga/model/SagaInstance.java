package com.bcra.orchestrator.saga.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "saga_instance")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SagaInstance {

    @Id
    @Column(name = "saga_id", nullable = false)
    private UUID sagaId;

    @Column(name = "conversation_id", length = 128, nullable = false)
    private String conversationId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private SagaType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private SagaStatus status;

    @Column(name = "current_step", nullable = false, length = 64)
    private String currentStep;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private String context;

    @Column(name = "timeout_at")
    private Instant timeoutAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private Long version;

    public SagaInstance(UUID sagaId, String conversationId, SagaType type, String currentStep, Instant now) {
        this.sagaId = Objects.requireNonNull(sagaId, "sagaId");
        this.conversationId = Objects.requireNonNull(conversationId, "conversationId");
        this.type = Objects.requireNonNull(type, "type");
        this.currentStep = Objects.requireNonNull(currentStep, "currentStep");
        this.status = SagaStatus.PENDING;
        this.context = "{}";
        this.createdAt = Objects.requireNonNull(now, "now");
        this.updatedAt = now;
    }

    public void recordProgress(SagaStatus status, String currentStep, String context, Instant timeoutAt, Instant now) {
        this.status = Objects.requireNonNull(status, "status");
        this.currentStep = Objects.requireNonNull(currentStep, "currentStep");
        this.context = Objects.requireNonNull(context, "context");
        this.timeoutAt = Objects.requireNonNull(timeoutAt, "timeoutAt");
        this.updatedAt = Objects.requireNonNull(now, "now");
    }
}
