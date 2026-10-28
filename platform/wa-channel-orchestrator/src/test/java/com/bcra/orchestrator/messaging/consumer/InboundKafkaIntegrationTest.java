package com.bcra.orchestrator.messaging.consumer;

import com.bcra.orchestrator.OrchestratorApplication;
import com.bcra.orchestrator.dedup.DedupeService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.ProducerRecord;
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
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@Tag("integration")
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(classes = {OrchestratorApplication.class, InboundTransactionTest.Effects.class})
@Import(InboundKafkaIntegrationTest.Topics.class)
class InboundKafkaIntegrationTest extends InboundTransactionTest {
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
        registry.add("spring.kafka.listener.auto-startup", () -> true);
    }

    private final KafkaTemplate<String, String> kafka;
    private final DedupeService dedupe;

    @Autowired
    InboundKafkaIntegrationTest(InboundEventConsumer consumer, org.springframework.jdbc.core.JdbcTemplate jdbc,
            KafkaTemplate<String, String> kafka, DedupeService dedupe) {
        super(consumer, jdbc);
        this.kafka = kafka;
        this.dedupe = dedupe;
    }

    @Test
    @DisplayName("Kafka preserva el orden, ignora duplicados y deriva inválidos a DLQ")
    void preservesBrokerOrderIgnoresDuplicateAndRecoversInvalidToDlq() throws Exception {
        String conversation = UUID.randomUUID().toString();
        List<String> messages = List.of(UUID.randomUUID().toString(), UUID.randomUUID().toString());
        send(InboundEventConsumerTest.record(messages.getFirst(), conversation));
        send(InboundEventConsumerTest.record(messages.getFirst(), conversation));
        send(InboundEventConsumerTest.record(messages.getLast(), conversation));
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            var effects = jdbc.queryForList("SELECT message_id FROM inbound_test_effect "
                    + "WHERE message_id IN (?, ?) ORDER BY effect_order", String.class,
                    messages.getFirst(), messages.getLast());
            assertThat(effects).containsExactlyElementsOf(messages);
        });
        try (var dlq = new KafkaConsumer<String, String>(Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, UUID.randomUUID().toString(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class))) {
            dlq.subscribe(List.of("wa.message.dlq.v1"));
            kafka.send("wa.message.received.v1", conversation, "{invalid-private-content")
                    .get(20, TimeUnit.SECONDS);
            await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
                var records = dlq.poll(Duration.ofMillis(200));
                assertThat(records.isEmpty()).isFalse();
                var recovered = records.iterator().next();
                assertThat(recovered.value()).doesNotContain("invalid-private-content");
                assertThat(new ObjectMapper().readTree(recovered.value()).path("payload")
                        .path("errorCode").asText()).isEqualTo("SCHEMA_VALIDATION_FAILED");
            });
        }
    }

    @Test
    @DisplayName("Dos reclamaciones concurrentes en PostgreSQL confirman un solo efecto")
    void concurrentDuplicateClaimsHaveOneCommittedEffect() throws Exception {
        String messageId = UUID.randomUUID().toString();
        var validator = new InboundEventValidator(new ObjectMapper());
        var message = validator.validate(InboundEventConsumerTest.record(messageId, "concurrent"));
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> dedupe.process(message));
            var second = executor.submit(() -> dedupe.process(message));
            assertThat(List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(true, false);
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM inbound_test_effect WHERE message_id = ?",
                Integer.class, messageId)).isEqualTo(1);
    }

    private void send(org.apache.kafka.clients.consumer.ConsumerRecord<String, String> record) throws Exception {
        kafka.send(new ProducerRecord<>(record.topic(), null, record.key(), record.value(), record.headers()))
                .get(20, TimeUnit.SECONDS);
    }

    @TestConfiguration
    static class Topics {
        @Bean
        KafkaAdmin.NewTopics inboundTopics() {
            return new KafkaAdmin.NewTopics(topic("wa.message.received.v1"), topic("wa.message.dlq.v1"));
        }

        private NewTopic topic(String name) {
            return TopicBuilder.name(name).partitions(3).replicas(1).build();
        }
    }
}
