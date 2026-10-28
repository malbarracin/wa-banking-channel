package com.bcra.orchestrator.saga.orchestrator;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bcra.orchestrator.saga.actions.SagaSignal;
import com.bcra.orchestrator.saga.compensation.SagaCompensation;
import com.bcra.orchestrator.saga.compensation.CompensationFailedException;
import com.bcra.orchestrator.saga.model.SagaInstance;
import com.bcra.orchestrator.saga.model.SagaStatus;
import com.bcra.orchestrator.saga.model.SagaStep;
import com.bcra.orchestrator.saga.model.SagaStepExecution;
import com.bcra.orchestrator.saga.model.SagaType;
import com.bcra.orchestrator.saga.repository.SagaInstanceRepository;
import com.bcra.orchestrator.saga.repository.SagaStepExecutionRepository;

@Service
public class SagaOrchestrator {
    private final SagaInstanceRepository sagas;
    private final SagaStepExecutionRepository executions;
    private final ObjectProvider<SagaCompensation> compensations;
    private final Duration stepTimeout;

    public SagaOrchestrator(SagaInstanceRepository sagas, SagaStepExecutionRepository executions,
            ObjectProvider<SagaCompensation> compensations,
            @Value("${orchestrator.saga.step-timeout:PT24H}") Duration stepTimeout) {
        if (stepTimeout.isNegative() || stepTimeout.isZero()) {
            throw new IllegalArgumentException("Saga timeout must be positive");
        }
        this.sagas = sagas;
        this.executions = executions;
        this.compensations = compensations;
        this.stepTimeout = stepTimeout;
    }

    /** Caller creates the conversation first; sagaId is the idempotency key for starting a flow. */
    @Transactional
    public SagaInstance start(UUID sagaId, String conversationId, SagaType type) {
        return sagas.findById(sagaId).map(existing -> {
            if (!existing.getConversationId().equals(conversationId) || existing.getType() != type) {
                throw new IllegalArgumentException("Saga identity mismatch");
            }
            return existing;
        }).orElseGet(() -> {
            Instant now = Instant.now();
            SagaStep first = SagaStep.flow(type).getFirst();
            SagaInstance saga = new SagaInstance(sagaId, conversationId, type, first.name(), now);
            move(saga, SagaStatus.IN_PROGRESS, first.name(), now);
            sagas.save(saga);
            executions.save(new SagaStepExecution(UUID.randomUUID(), sagaId, first.name(), 1, now));
            return saga;
        });
    }

    /** Signals come from confirmed H9/H7 results, never from interpreting message text. */
    @Transactional
    public SagaInstance signal(UUID sagaId, SagaStep expectedStep, SagaSignal signal) {
        Objects.requireNonNull(signal, "signal");
        SagaInstance saga = sagas.lockById(sagaId).orElseThrow();
        if (!isActive(saga) || !saga.getCurrentStep().equals(expectedStep.name())) {
            throw new IllegalStateException("Invalid saga transition");
        }
        Instant now = Instant.now();
        if (!saga.getTimeoutAt().isAfter(now)) {
            compensate(saga, "TIMEOUT", now);
        } else if (signal == SagaSignal.FAILED) {
            compensate(saga, "STEP_FAILED", now);
        } else if (signal == SagaSignal.WAIT_FOR_INPUT) {
            move(saga, SagaStatus.WAITING, expectedStep.name(), now);
        } else {
            completeStep(saga, now);
        }
        return saga;
    }

    @Transactional
    public void expire(UUID sagaId, Instant now) {
        sagas.lockById(sagaId).filter(this::isActive)
                .filter(saga -> !saga.getTimeoutAt().isAfter(now))
                .ifPresent(saga -> compensate(saga, "TIMEOUT", now));
    }

    private boolean isActive(SagaInstance saga) {
        return saga.getStatus() == SagaStatus.IN_PROGRESS || saga.getStatus() == SagaStatus.WAITING;
    }

    private void completeStep(SagaInstance saga, Instant now) {
        executions.findBySagaIdOrderByStartedAtAsc(saga.getSagaId()).stream()
                .filter(step -> step.getStep().equals(saga.getCurrentStep()))
                .forEach(step -> step.recordResult(SagaStatus.COMPLETED, "SUCCEEDED", now));
        List<SagaStep> flow = SagaStep.flow(saga.getType());
        int next = flow.indexOf(SagaStep.valueOf(saga.getCurrentStep())) + 1;
        if (next == flow.size()) {
            move(saga, SagaStatus.COMPLETED, saga.getCurrentStep(), now);
        } else {
            String step = flow.get(next).name();
            move(saga, SagaStatus.IN_PROGRESS, step, now);
            executions.save(new SagaStepExecution(UUID.randomUUID(), saga.getSagaId(), step, 1, now));
        }
    }

    private void compensate(SagaInstance saga, String reason, Instant now) {
        move(saga, SagaStatus.COMPENSATING, saga.getCurrentStep(), now);
        List<SagaStepExecution> steps = new ArrayList<>(
                executions.findBySagaIdOrderByStartedAtAsc(saga.getSagaId()));
        List<SagaStep> flow = SagaStep.flow(saga.getType());
        steps.sort(Comparator.comparingInt((SagaStepExecution step) ->
                flow.indexOf(SagaStep.valueOf(step.getStep()))).reversed());
        boolean confirmed = true;
        SagaCompensation adapter = compensations.getIfAvailable();
        for (SagaStepExecution step : steps) {
            // Include the failing step: a timeout can hide a successful remote effect.
            boolean compensated = compensateStep(adapter, saga, step);
            step.recordResult(compensated ? SagaStatus.COMPENSATED : SagaStatus.FAILED,
                    compensated ? reason : "SUPPORT_REQUIRED", now);
            confirmed &= compensated;
        }
        move(saga, confirmed ? SagaStatus.COMPENSATED : SagaStatus.FAILED, saga.getCurrentStep(), now);
    }

    private boolean compensateStep(SagaCompensation adapter, SagaInstance saga, SagaStepExecution step) {
        try {
            return adapter != null && adapter.compensate(saga.getSagaId(), SagaStep.valueOf(step.getStep()));
        } catch (CompensationFailedException failure) {
            // The persisted result is the support handoff; remote details are deliberately omitted.
            return false;
        }
    }

    private void move(SagaInstance saga, SagaStatus status, String step, Instant now) {
        saga.recordProgress(status, step, "{}", now.plus(stepTimeout), now);
    }
}
