package com.bcra.orchestrator.messaging.config;

import com.bcra.orchestrator.messaging.consumer.InvalidEventException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("integration")
@Testcontainers(disabledWithoutDocker = true)
class KafkaDlqIntegrationTest {
    @Container
    static final KafkaContainer KAFKA = new KafkaContainer(DockerImageName.parse("apache/kafka:3.9.1"));

    @Test
    void publishesContractEnvelopeToRealBroker() throws Exception {
        try (var admin = AdminClient.create(Map.of("bootstrap.servers", KAFKA.getBootstrapServers()))) {
            admin.createTopics(List.of(new NewTopic(SafeDeadLetterRecoverer.TOPIC, 3, (short) 1)))
                    .all().get(20, java.util.concurrent.TimeUnit.SECONDS);
        }
        var factory = new DefaultKafkaProducerFactory<String, String>(Map.of(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers(),
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
                ProducerConfig.ACKS_CONFIG, "all", ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true));
        var template = new KafkaTemplate<>(factory);
        try (var consumer = new KafkaConsumer<String, String>(Map.of(
                        ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers(),
                        ConsumerConfig.GROUP_ID_CONFIG, "dlq-test",
                        ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                        ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                        ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class))) {
            var mapper = new ObjectMapper();
            var recoverer = new SafeDeadLetterRecoverer(template, mapper, new SimpleMeterRegistry());
            recoverer.accept(new ConsumerRecord<>("wa.message.received.v1", 0, 12, "private", "private"),
                    consumer, new InvalidEventException());
            consumer.subscribe(List.of(SafeDeadLetterRecoverer.TOPIC));
            long deadline = System.nanoTime() + Duration.ofSeconds(20).toNanos();
            ConsumerRecord<String, String> received = null;
            while (received == null && System.nanoTime() < deadline) {
                var records = consumer.poll(Duration.ofMillis(200));
                if (!records.isEmpty()) {
                    received = records.iterator().next();
                }
            }
            assertThat(received).isNotNull();
            assertThat(received.value()).doesNotContain("private");
            assertThat(mapper.readTree(received.value()).path("payload").path("errorCode").asText())
                    .isEqualTo("SCHEMA_VALIDATION_FAILED");
        } finally {
            template.destroy();
            factory.destroy();
        }
    }
}
