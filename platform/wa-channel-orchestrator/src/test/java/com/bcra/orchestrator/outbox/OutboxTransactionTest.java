package com.bcra.orchestrator.outbox;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import com.bcra.orchestrator.conversation.model.ConversationState;
import com.bcra.orchestrator.messaging.producer.OutboundEventPublisher;
import com.bcra.orchestrator.outbox.model.OutboxEvent;
import com.bcra.orchestrator.outbox.repository.OutboxEventRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.persistence.EntityManager;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import({OutboundEventPublisher.class, OutboxDispatcher.class, OutboxTransactionTest.Infrastructure.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class OutboxTransactionTest {
    private final OutboundEventPublisher publisher;
    private final OutboxDispatcher dispatcher;
    private final OutboxEventRepository repository;
    private final EntityManager entityManager;
    private final PlatformTransactionManager transactions;
    private final KafkaTemplate<String, String> kafka;

    @Autowired
    OutboxTransactionTest(OutboundEventPublisher publisher, OutboxDispatcher dispatcher,
            OutboxEventRepository repository, EntityManager entityManager,
            PlatformTransactionManager transactions, KafkaTemplate<String, String> kafka) {
        this.publisher = publisher;
        this.dispatcher = dispatcher;
        this.repository = repository;
        this.entityManager = entityManager;
        this.transactions = transactions;
        this.kafka = kafka;
    }

    @Test
    @DisplayName("Exige transacción del turno y revierte outbox junto con conversación")
    void requiresCallerTransactionAndRollsBackWithConversation() {
        String conversation = UUID.randomUUID().toString();
        UUID correlation = UUID.randomUUID();
        assertThatThrownBy(() -> publisher.publish(conversation, correlation, "m", "test", "test"))
                .isInstanceOf(org.springframework.transaction.IllegalTransactionStateException.class);
        UUID event = new TransactionTemplate(transactions).execute(status -> {
            entityManager.persist(new ConversationState(conversation, correlation, "ACTIVE", Instant.now()));
            UUID id = publisher.publish(conversation, correlation, UUID.randomUUID().toString(), "test", "test");
            entityManager.flush();
            status.setRollbackOnly();
            return id;
        });
        assertThat(repository.findById(event)).isEmpty();
        assertThat(entityManager.find(ConversationState.class, conversation)).isNull();
    }

    @Test
    @DisplayName("Publica tras commit y rechaza duplicados sin reemplazar el evento original")
    void committedRowIsDispatchedAndDuplicateCannotReplaceOriginal() {
        reset(kafka);
        when(kafka.send(any(ProducerRecord.class))).thenReturn(CompletableFuture.completedFuture(null));
        String conversation = UUID.randomUUID().toString();
        UUID correlation = UUID.randomUUID();
        String message = UUID.randomUUID().toString();
        var transaction = new TransactionTemplate(transactions);
        UUID event = transaction.execute(status -> {
            entityManager.persist(new ConversationState(conversation, correlation, "ACTIVE", Instant.now()));
            return publisher.publish(conversation, correlation, message, "test-address", "test-body");
        });
        verifyNoInteractions(kafka);
        assertThatThrownBy(() -> transaction.execute(status ->
                publisher.publish(conversation, correlation, message, "test-address", "changed-body")))
                .isInstanceOf(RuntimeException.class);
        assertThat(repository.findById(event).orElseThrow().getPayload()).contains("test-body");
        dispatcher.dispatchNext();
        OutboxEvent sent = repository.findById(event).orElseThrow();
        assertThat(sent.getPublishedAt()).isNotNull();
        dispatcher.dispatchNext();
        verify(kafka, times(1)).send(any(ProducerRecord.class));
    }

    @TestConfiguration
    static class Infrastructure {
        @Bean
        ObjectMapper mapper() {
            return new ObjectMapper();
        }

        @Bean
        SimpleMeterRegistry metrics() {
            return new SimpleMeterRegistry();
        }

        @Bean
        KafkaTemplate<String, String> kafka() {
            return mock(KafkaTemplate.class);
        }
    }
}
