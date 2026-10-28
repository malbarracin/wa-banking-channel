package com.bcra.orchestrator.conversation.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.bcra.orchestrator.conversation.model.ConversationState;

public interface ConversationStateRepository extends JpaRepository<ConversationState, String> {
}
