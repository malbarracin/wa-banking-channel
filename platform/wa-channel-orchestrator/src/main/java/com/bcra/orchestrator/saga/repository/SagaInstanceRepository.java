package com.bcra.orchestrator.saga.repository;

import java.util.UUID;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import org.springframework.data.jpa.repository.JpaRepository;

import com.bcra.orchestrator.saga.model.SagaInstance;

public interface SagaInstanceRepository extends JpaRepository<SagaInstance, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from SagaInstance s where s.sagaId = :id")
    Optional<SagaInstance> lockById(UUID id);

    @Query("select s.sagaId from SagaInstance s where s.timeoutAt <= :now "
            + "and s.status in (com.bcra.orchestrator.saga.model.SagaStatus.IN_PROGRESS, "
            + "com.bcra.orchestrator.saga.model.SagaStatus.WAITING)")
    List<UUID> findExpired(Instant now);
}
