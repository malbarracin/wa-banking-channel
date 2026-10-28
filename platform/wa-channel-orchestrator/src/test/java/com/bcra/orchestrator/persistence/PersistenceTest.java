package com.bcra.orchestrator.persistence;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.OptimisticLockException;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import com.bcra.orchestrator.conversation.model.ConversationState;
import com.bcra.orchestrator.conversation.repository.ConversationStateRepository;
import com.bcra.orchestrator.dedup.model.MessageDedupe;
import com.bcra.orchestrator.dedup.repository.MessageDedupeRepository;
import com.bcra.orchestrator.outbox.model.OutboxEvent;
import com.bcra.orchestrator.outbox.repository.OutboxEventRepository;
import com.bcra.orchestrator.saga.model.SagaInstance;
import com.bcra.orchestrator.saga.model.SagaStatus;
import com.bcra.orchestrator.saga.model.SagaStepExecution;
import com.bcra.orchestrator.saga.model.SagaType;
import com.bcra.orchestrator.saga.repository.SagaInstanceRepository;
import com.bcra.orchestrator.saga.repository.SagaStepExecutionRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class PersistenceTest {

    private static final Instant NOW = Instant.parse("2026-10-28T10:00:00Z");

    @Autowired
    private Flyway flyway;
    @Autowired
    private ConversationStateRepository conversations;
    @Autowired
    private SagaInstanceRepository sagas;
    @Autowired
    private SagaStepExecutionRepository steps;
    @Autowired
    private MessageDedupeRepository dedupe;
    @Autowired
    private OutboxEventRepository outbox;
    @Autowired
    private EntityManager entityManager;
    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Test
    @DisplayName("Aplica las cinco migraciones Flyway")
    void appliesFiveMigrations() {
        assertThat(flyway.info().applied()).hasSize(6);
        assertThat(flyway.info().pending()).isEmpty();
    }

    @Test
    @DisplayName("Persiste y recupera conversación, saga, paso, deduplicación y outbox")
    void reloadsAllModels() {
        String conversationId = UUID.randomUUID().toString();
        UUID correlationId = UUID.randomUUID();
        var conversation = new ConversationState(conversationId, correlationId, "ACTIVE", NOW);
        conversation.recordMessage("inbound-1", correlationId, NOW);
        conversations.saveAndFlush(conversation);
        var saga = new SagaInstance(UUID.randomUUID(), conversationId, SagaType.ONBOARDING, "LINK", NOW);
        saga.recordProgress(SagaStatus.WAITING, "LINK", "{}", NOW.plusSeconds(60), NOW);
        sagas.saveAndFlush(saga);
        var step = new SagaStepExecution(UUID.randomUUID(), saga.getSagaId(), "LINK", 1, NOW);
        step.recordResult(SagaStatus.COMPLETED, "OK", NOW);
        steps.saveAndFlush(step);
        var processed = new MessageDedupe(UUID.randomUUID(), "inbound-1", conversationId,
                correlationId, "MessageReceived", NOW);
        dedupe.saveAndFlush(processed);
        var event = new OutboxEvent(UUID.randomUUID(), conversationId, correlationId, "outbound-1",
                "MessageReadyToSend", 1, "wa.message.ready-to-send.v1", "{}", NOW);
        event.markPublished(NOW);
        outbox.saveAndFlush(event);
        entityManager.clear();

        var loadedConversation = conversations.findById(conversationId).orElseThrow();
        assertThat(loadedConversation.getCorrelationId()).isEqualTo(correlationId);
        assertThat(loadedConversation.getState()).isEqualTo("ACTIVE");
        assertThat(loadedConversation.getLastMessageId()).isEqualTo("inbound-1");
        assertThat(loadedConversation.getUpdatedAt()).isEqualTo(NOW);
        assertThat(loadedConversation.getVersion()).isZero();
        var loadedSaga = sagas.findById(saga.getSagaId()).orElseThrow();
        assertThat(loadedSaga.getConversationId()).isEqualTo(conversationId);
        assertThat(loadedSaga.getType()).isEqualTo(SagaType.ONBOARDING);
        assertThat(loadedSaga.getStatus()).isEqualTo(SagaStatus.WAITING);
        assertThat(loadedSaga.getCurrentStep()).isEqualTo("LINK");
        assertThat(loadedSaga.getContext()).isEqualTo("{}");
        assertThat(loadedSaga.getTimeoutAt()).isEqualTo(NOW.plusSeconds(60));
        assertThat(loadedSaga.getCreatedAt()).isEqualTo(NOW);
        assertThat(loadedSaga.getUpdatedAt()).isEqualTo(NOW);
        var loadedStep = steps.findById(step.getExecutionId()).orElseThrow();
        assertThat(loadedStep.getSagaId()).isEqualTo(saga.getSagaId());
        assertThat(loadedStep.getStep()).isEqualTo("LINK");
        assertThat(loadedStep.getAttempt()).isEqualTo(1);
        assertThat(loadedStep.getStatus()).isEqualTo(SagaStatus.COMPLETED);
        assertThat(loadedStep.getResultCode()).isEqualTo("OK");
        assertThat(loadedStep.getStartedAt()).isEqualTo(NOW);
        assertThat(loadedStep.getCompletedAt()).isEqualTo(NOW);
        var loadedDedupe = dedupe.findById(processed.getId()).orElseThrow();
        assertThat(loadedDedupe.getMessageId()).isEqualTo("inbound-1");
        assertThat(loadedDedupe.getConversationId()).isEqualTo(conversationId);
        assertThat(loadedDedupe.getCorrelationId()).isEqualTo(correlationId);
        assertThat(loadedDedupe.getEventType()).isEqualTo("MessageReceived");
        assertThat(loadedDedupe.getProcessedAt()).isEqualTo(NOW);
        var loadedEvent = outbox.findById(event.getEventId()).orElseThrow();
        assertThat(loadedEvent.getConversationId()).isEqualTo(conversationId);
        assertThat(loadedEvent.getCorrelationId()).isEqualTo(correlationId);
        assertThat(loadedEvent.getMessageId()).isEqualTo("outbound-1");
        assertThat(loadedEvent.getEventType()).isEqualTo("MessageReadyToSend");
        assertThat(loadedEvent.getEventVersion()).isEqualTo(1);
        assertThat(loadedEvent.getTopic()).isEqualTo("wa.message.ready-to-send.v1");
        assertThat(loadedEvent.getPayload()).isEqualTo("{}");
        assertThat(loadedEvent.getCreatedAt()).isEqualTo(NOW);
        assertThat(loadedEvent.getPublishedAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("Rechaza el mismo messageId incluso en conversaciones distintas")
    void rejectsDuplicateMessageId() {
        String messageId = UUID.randomUUID().toString();
        dedupe.saveAndFlush(new MessageDedupe(UUID.randomUUID(), messageId, "conversation-a",
                UUID.randomUUID(), "MessageReceived", NOW));

        assertThatThrownBy(() -> dedupe.saveAndFlush(new MessageDedupe(UUID.randomUUID(), messageId,
                "conversation-b", UUID.randomUUID(), "MessageReceived", NOW)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("Detecta actualizaciones concurrentes de saga por versión")
    void rejectsStaleSagaUpdate() {
        String conversationId = UUID.randomUUID().toString();
        UUID sagaId = UUID.randomUUID();
        EntityManager first = entityManagerFactory.createEntityManager();
        EntityManager second = entityManagerFactory.createEntityManager();
        try {
            first.getTransaction().begin();
            first.persist(new ConversationState(conversationId, UUID.randomUUID(), "ACTIVE", NOW));
            first.persist(new SagaInstance(sagaId, conversationId, SagaType.REVERIFICATION, "VERIFY", NOW));
            first.getTransaction().commit();
            first.clear();
            first.getTransaction().begin();
            second.getTransaction().begin();
            SagaInstance latest = first.find(SagaInstance.class, sagaId);
            SagaInstance stale = second.find(SagaInstance.class, sagaId);
            latest.recordProgress(SagaStatus.WAITING, "VERIFY", "{}", NOW.plusSeconds(60), NOW);
            first.getTransaction().commit();

            stale.recordProgress(SagaStatus.FAILED, "VERIFY", "{}", NOW.plusSeconds(60), NOW);
            assertThatThrownBy(second::flush).isInstanceOf(OptimisticLockException.class);
            second.getTransaction().rollback();
            first.clear();
            assertThat(first.find(SagaInstance.class, sagaId).getVersion()).isEqualTo(1L);
        } finally {
            if (first.getTransaction().isActive()) {
                first.getTransaction().rollback();
            }
            if (second.getTransaction().isActive()) {
                second.getTransaction().rollback();
            }
            first.close();
            second.close();
        }
    }

    @Test
    @DisplayName("No permite intentos ni versiones de evento no positivos")
    void rejectsInvalidCounters() {
        assertThatThrownBy(() -> new SagaStepExecution(UUID.randomUUID(), UUID.randomUUID(), "LINK", 0, NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new OutboxEvent(UUID.randomUUID(), "conversation", UUID.randomUUID(),
                "message", "MessageReadyToSend", 0, "topic", "{}", NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Rollback no deja estado, deduplicación ni outbox parcialmente persistidos")
    void rollsBackStateDedupeAndOutboxTogether() {
        String conversationId = UUID.randomUUID().toString();
        UUID correlationId = UUID.randomUUID();
        UUID dedupeId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        EntityManager writer = entityManagerFactory.createEntityManager();
        try {
            writer.getTransaction().begin();
            writer.persist(new ConversationState(conversationId, correlationId, "ACTIVE", NOW));
            writer.persist(new MessageDedupe(dedupeId, UUID.randomUUID().toString(), conversationId,
                    correlationId, "MessageReceived", NOW));
            writer.persist(new OutboxEvent(eventId, conversationId, correlationId, "outbound-rollback",
                    "MessageReadyToSend", 1, "wa.message.ready-to-send.v1", "{}", NOW));
            writer.flush();
            writer.getTransaction().rollback();
            writer.clear();

            assertThat(writer.find(ConversationState.class, conversationId)).isNull();
            assertThat(writer.find(MessageDedupe.class, dedupeId)).isNull();
            assertThat(writer.find(OutboxEvent.class, eventId)).isNull();
        } finally {
            if (writer.getTransaction().isActive()) {
                writer.getTransaction().rollback();
            }
            writer.close();
        }
    }
}
