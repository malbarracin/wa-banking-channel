package com.bcra.orchestrator.saga.compensation;

import java.util.UUID;

import com.bcra.orchestrator.saga.model.SagaStep;

/** Adapter must be idempotent by sagaId + step and confirm the remote compensation. */
public interface SagaCompensation {
    boolean compensate(UUID sagaId, SagaStep step);
}
