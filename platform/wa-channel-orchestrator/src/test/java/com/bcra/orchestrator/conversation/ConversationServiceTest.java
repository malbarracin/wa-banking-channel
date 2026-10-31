package com.bcra.orchestrator.conversation;

import java.util.UUID;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.bcra.orchestrator.conversation.model.ConversationState;
import com.bcra.orchestrator.conversation.repository.ConversationStateRepository;
import com.bcra.orchestrator.conversation.service.ConversationService;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
public class ConversationServiceTest {

    @Autowired
    private ConversationService service;

    @Autowired
    private ConversationStateRepository conversations;

    @Autowired
    private PlatformTransactionManager transactions;

    @Autowired
    private EntityManager entityManager;

    @Test
    @DisplayName("Crea y recupera el contexto mínimo sin reiniciar el progreso")
    void loadsPersistedProgress() {
        String conversationId = UUID.randomUUID().toString();
        UUID correlationId = UUID.randomUUID();
        ConversationState initial = service.loadOrCreate(conversationId, correlationId);
        assertThat(initial.getState()).isEqualTo("INITIAL");
        assertThat(initial.getCorrelationId()).isEqualTo(correlationId);
        assertThat(initial.getLastMessageId()).isNull();

        UUID nextCorrelation = UUID.randomUUID();
        service.updateState(conversationId, nextCorrelation, "WAITING", "message-1");
        ConversationState loaded = service.loadOrCreate(conversationId, UUID.randomUUID());

        assertThat(loaded.getState()).isEqualTo("WAITING");
        assertThat(loaded.getLastMessageId()).isEqualTo("message-1");
        assertThat(loaded.getCorrelationId()).isEqualTo(nextCorrelation);
        assertThat(loaded.getUpdatedAt()).isAfterOrEqualTo(initial.getUpdatedAt());
        assertThat(loaded.getVersion()).isGreaterThan(initial.getVersion());
    }

    @Test
    @DisplayName("Revierte el cambio junto con la transacción del llamador")
    void rollsBackStateUpdate() {
        String conversationId = UUID.randomUUID().toString();
        UUID correlationId = UUID.randomUUID();
        service.loadOrCreate(conversationId, correlationId);

        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            service.updateState(conversationId, UUID.randomUUID(), "WAITING", "message-2");
            entityManager.flush();
            status.setRollbackOnly();
        });

        ConversationState persisted = conversations.findById(conversationId).orElseThrow();
        assertThat(persisted.getState()).isEqualTo("INITIAL");
        assertThat(persisted.getLastMessageId()).isNull();
        assertThat(persisted.getCorrelationId()).isEqualTo(correlationId);
    }

    @Test
    @DisplayName("Inicializa una conversación al registrar su primer estado")
    void updatesNewConversation() {
        String conversationId = UUID.randomUUID().toString();
        UUID correlationId = UUID.randomUUID();

        service.updateState(conversationId, correlationId, "WAITING", "message-3");

        ConversationState persisted = conversations.findById(conversationId).orElseThrow();
        assertThat(persisted.getState()).isEqualTo("WAITING");
        assertThat(persisted.getLastMessageId()).isEqualTo("message-3");
        assertThat(persisted.getCorrelationId()).isEqualTo(correlationId);
    }

    @Test
    @DisplayName("Revierte también la creación de contexto en la transacción del llamador")
    void rollsBackConversationCreation() {
        String conversationId = UUID.randomUUID().toString();

        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            service.loadOrCreate(conversationId, UUID.randomUUID());
            entityManager.flush();
            status.setRollbackOnly();
        });

        assertThat(conversations.findById(conversationId)).isEmpty();
    }

    @Test
    @DisplayName("Mantiene independientes dos conversaciones con la misma correlación")
    void isolatesConversationsSharingCorrelation() {
        String firstId = UUID.randomUUID().toString();
        String secondId = UUID.randomUUID().toString();
        UUID correlationId = UUID.randomUUID();
        service.loadOrCreate(firstId, correlationId);
        ConversationState second = service.loadOrCreate(secondId, correlationId);

        service.updateState(firstId, UUID.randomUUID(), "WAITING", "message-isolation");

        ConversationState persisted = conversations.findById(secondId).orElseThrow();
        assertThat(persisted.getConversationId()).isEqualTo(secondId);
        assertThat(persisted.getState()).isEqualTo("INITIAL");
        assertThat(persisted.getLastMessageId()).isNull();
        assertThat(persisted.getCorrelationId()).isEqualTo(correlationId);
        assertThat(persisted.getVersion()).isEqualTo(second.getVersion());
    }
}
