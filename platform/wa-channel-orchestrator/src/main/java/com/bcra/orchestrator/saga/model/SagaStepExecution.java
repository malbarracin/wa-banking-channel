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

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "saga_step_execution")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SagaStepExecution {

    @Id
    @Column(name = "execution_id", nullable = false)
    private UUID executionId;

    @Column(name = "saga_id", nullable = false)
    private UUID sagaId;

    @Column(name = "step", nullable = false, length = 64)
    private String step;

    @Column(name = "attempt", nullable = false)
    private int attempt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private SagaStatus status;

    @Column(name = "result_code", length = 64)
    private String resultCode;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    public SagaStepExecution(UUID executionId, UUID sagaId, String step, int attempt, Instant startedAt) {
        if (attempt < 1) {
            throw new IllegalArgumentException("attempt must be positive");
        }
        this.executionId = Objects.requireNonNull(executionId, "executionId");
        this.sagaId = Objects.requireNonNull(sagaId, "sagaId");
        this.step = Objects.requireNonNull(step, "step");
        this.attempt = attempt;
        this.startedAt = Objects.requireNonNull(startedAt, "startedAt");
        this.status = SagaStatus.IN_PROGRESS;
    }

    public void recordResult(SagaStatus status, String resultCode, Instant completedAt) {
        this.status = Objects.requireNonNull(status, "status");
        this.resultCode = Objects.requireNonNull(resultCode, "resultCode");
        this.completedAt = Objects.requireNonNull(completedAt, "completedAt");
    }
}
