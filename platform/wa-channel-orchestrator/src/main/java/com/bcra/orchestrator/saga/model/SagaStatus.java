package com.bcra.orchestrator.saga.model;

public enum SagaStatus {
    PENDING, IN_PROGRESS, WAITING, COMPLETED, FAILED, COMPENSATING, COMPENSATED
}
