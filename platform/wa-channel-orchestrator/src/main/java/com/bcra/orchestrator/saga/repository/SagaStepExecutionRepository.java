package com.bcra.orchestrator.saga.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.bcra.orchestrator.saga.model.SagaStepExecution;

public interface SagaStepExecutionRepository extends JpaRepository<SagaStepExecution, UUID> {
}
