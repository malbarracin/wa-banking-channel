package com.bcra.orchestrator.outbox.repository;

import java.util.UUID;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;

import org.springframework.data.jpa.repository.JpaRepository;

import com.bcra.orchestrator.outbox.model.OutboxEvent;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<OutboxEvent> findFirstByPublishedAtIsNullAndDeadLetteredAtIsNullOrderByCreatedAtAscEventIdAsc();
}
