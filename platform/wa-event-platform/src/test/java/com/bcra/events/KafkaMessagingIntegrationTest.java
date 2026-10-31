package com.bcra.events;

import com.bcra.events.dlq.DeadLetterPublisher;
import com.bcra.events.messaging.producer.KafkaEventProducer;
import com.bcra.events.messaging.schema.EventSchemaValidator;
import com.bcra.events.messaging.schema.InvalidEventException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.annotation.DirtiesContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

@SpringBootTest
@EmbeddedKafka(kraft = true, partitions = 3, bootstrapServersProperty = "spring.kafka.bootstrap-servers")
@DirtiesContext
class KafkaMessagingIntegrationTest {
    @Autowired
    private KafkaProperties properties;
    @Autowired
    private KafkaEventProducer producer;
    @Autowired
    private DefaultErrorHandler errorHandler;
    @Autowired
    private EventSchemaValidator validator;
    @Autowired
    private ConcurrentKafkaListenerContainerFactory<?, ?> factory;

    @Test
    @DisplayName("La configuración efectiva usa MANUAL y productor idempotente con acks all")
    void usesRequiredProducerAndConsumerConfiguration() {
        assertThat(factory.getContainerProperties().getAckMode()).isEqualTo(ContainerProperties.AckMode.MANUAL);
        assertThat(properties.getConsumer().getEnableAutoCommit()).isFalse();
        assertThat(properties.getProducer().getAcks()).isEqualTo("all");
        assertThat(properties.getProducer().getProperties()).containsEntry("enable.idempotence", "true");
    }

    @Test
    @DisplayName("Publica evento validado y recupera evento inválido en Kafka KRaft real")
    void publishesValidatedEventAndRecoversInvalidEventToBroker() throws Exception {
        String status = """
                {"eventId":"11111111-1111-4111-8111-111111111111","eventType":"MessageStatus",
                 "eventVersion":1,"occurredAt":"2026-01-01T12:00:00Z",
                 "correlationId":"11111111-1111-4111-8111-111111111111",
                 "conversationId":"synthetic-conversation","messageId":"synthetic-message",
                 "payload":{"status":"delivered"}}
                """;
        var sent = producer.send("wa.message.status.v1", status).get(10, TimeUnit.SECONDS);
        assertThat(sent.getProducerRecord().key()).isEqualTo("synthetic-conversation");
        var invalid = new ConsumerRecord<>("wa.message.received.v1", 0, 42, "synthetic-conversation", "{}");
        assertThat(errorHandler.handleOne(new InvalidEventException(), invalid, null,
                mock(MessageListenerContainer.class))).isTrue();
        Map<String, Object> settings = Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, properties.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, "dlq-verification",
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        try (var consumer = new KafkaConsumer<>(settings, new StringDeserializer(), new StringDeserializer())) {
            consumer.subscribe(java.util.List.of(DeadLetterPublisher.TOPIC));
            var recovered = KafkaTestUtils.getSingleRecord(consumer, DeadLetterPublisher.TOPIC,
                    Duration.ofSeconds(10));
            validator.validate(recovered.topic(), recovered.value());
            assertThat(recovered.key()).isEqualTo("synthetic-conversation");
            assertThat(recovered.value()).contains("SCHEMA_VALIDATION_FAILED");
            assertThat(recovered.headers()).extracting(header -> header.key())
                    .containsExactly("correlationId", "eventType", "eventVersion", "errorCode");
        }
    }
}
