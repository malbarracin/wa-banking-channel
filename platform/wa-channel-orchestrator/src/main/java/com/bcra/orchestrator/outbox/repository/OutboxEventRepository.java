package com.bcra.orchestrator.outbox.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.bcra.orchestrator.outbox.model.OutboxEvent;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {
}
