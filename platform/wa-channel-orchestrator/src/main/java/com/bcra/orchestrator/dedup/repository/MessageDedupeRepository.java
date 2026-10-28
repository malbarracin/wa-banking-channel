package com.bcra.orchestrator.dedup.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.bcra.orchestrator.dedup.model.MessageDedupe;

public interface MessageDedupeRepository extends JpaRepository<MessageDedupe, UUID> {
}
