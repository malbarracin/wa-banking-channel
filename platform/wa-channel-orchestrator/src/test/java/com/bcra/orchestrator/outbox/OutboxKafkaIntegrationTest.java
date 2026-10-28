package com.bcra.orchestrator.outbox;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.bcra.orchestrator.conversation.model.ConversationState;
import com.bcra.orchestrator.messaging.producer.OutboundEventPublisher;
import com.bcra.orchestrator.outbox.repository.OutboxEventRepository;
import jakarta.persistence.EntityManager;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@Tag("integration")
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(properties = {"outbox.enabled=false", "spring.kafka.listener.auto-startup=false"})
@Import(OutboxKafkaIntegrationTest.Topics.class)
class OutboxKafkaIntegrationTest {
    @Container
    static final KafkaContainer KAFKA = new KafkaContainer("apache/kafka:3.9.1");
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16.6-alpine");

    @DynamicPropertySource
    static void infrastructure(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
    }

    private final OutboundEventPublisher publisher;
    private final OutboxDispatcher dispatcher;
    private final OutboxEventRepository repository;
    private final EntityManager entityManager;
    private final PlatformTransactionManager transactions;

    @Autowired
    OutboxKafkaIntegrationTest(OutboundEventPublisher publisher, OutboxDispatcher dispatcher,
            OutboxEventRepository repository, EntityManager entityManager,
            PlatformTransactionManager transactions) {
        this.publisher = publisher;
        this.dispatcher = dispatcher;
        this.repository = repository;
        this.entityManager = entityManager;
        this.transactions = transactions;
    }

    @Test
    @DisplayName("Kafka recibe eventos confirmados en orden y no recibe el evento revertido")
    void commitPublishesInOrderAndRollbackProducesNothing() {
        String conversation = UUID.randomUUID().toString();
        UUID correlation = UUID.randomUUID();
        var transaction = new TransactionTemplate(transactions);
        transaction.executeWithoutResult(status -> entityManager.persist(
                new ConversationState(conversation, correlation, "ACTIVE", Instant.now())));
        UUID rolledBack = transaction.execute(status -> {
            UUID id = publisher.publish(conversation, correlation, "rolled-back", "test", "test");
            entityManager.flush();
            status.setRollbackOnly();
            return id;
        });
        List<UUID> events = new ArrayList<>();
        for (String message : List.of("first", "second")) {
            events.add(transaction.execute(status -> publisher.publish(conversation, correlation,
                    message, "test-address", "test-body")));
        }
        assertThat(repository.findById(rolledBack)).isEmpty();
        try (var consumer = new KafkaConsumer<String, String>(Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, UUID.randomUUID().toString(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class))) {
            consumer.subscribe(List.of(OutboundEventPublisher.TOPIC));
            dispatcher.dispatchNext();
            dispatcher.dispatchNext();
            dispatcher.dispatchNext();
            List<String> messages = new ArrayList<>();
            await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
                consumer.poll(Duration.ofMillis(200)).forEach(record -> {
                    assertThat(record.key()).isEqualTo(conversation);
                    assertThat(record.headers().lastHeader("correlationId").value())
                            .isEqualTo(correlation.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
                    messages.add(publisher.validate(record.value()).path("messageId").asText());
                });
                assertThat(messages).containsExactly("first", "second");
            });
            events.forEach(id -> assertThat(repository.findById(id).orElseThrow().getPublishedAt()).isNotNull());
        }
    }

    @TestConfiguration
    static class Topics {
        @Bean
        KafkaAdmin.NewTopics outboundTopics() {
            return new KafkaAdmin.NewTopics(TopicBuilder.name(OutboundEventPublisher.TOPIC)
                    .partitions(3).replicas(1).build());
        }
    }
}
