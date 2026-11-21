package com.bcra.events.messaging.dlq;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bcra.events.config.TopicsProperties;
import com.bcra.events.domain.EventEnvelope;
import com.bcra.events.domain.EventType;
import com.bcra.events.messaging.consumer.EnvelopeReader;
import com.bcra.events.observability.EventMetrics;
import com.bcra.events.schema.SchemaCatalog;
import com.bcra.events.schema.SchemaValidationException;
import com.bcra.events.schema.SchemaValidator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;

class DlqReprocessorTest {
    private final TopicsProperties topics = new TopicsProperties(
        "wa.message.received.v1", "wa.message.ready-to-send.v1", "wa.message.status.v1", "wa.message.dlq.v1",
        new TopicsProperties.ConsumerGroups("g", "o", "r"));
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules()
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    private final EnvelopeReader envelopeReader = new EnvelopeReader(mapper,
        new SchemaValidator(new SchemaCatalog()), new EventMetrics(new SimpleMeterRegistry()));

    @Test
    void republishesOriginalKeyValueToOriginalTopic() throws Exception {
        @SuppressWarnings("unchecked")
        ConsumerFactory<String, String> factory = mock(ConsumerFactory.class);
        @SuppressWarnings("unchecked")
        Consumer<String, String> consumer = mock(Consumer.class);
        @SuppressWarnings("unchecked")
        KafkaTemplate<String, String> kafka = mock(KafkaTemplate.class);
        when(factory.createConsumer(eq("wa-event-platform-dlq-reprocess"), eq(null))).thenReturn(consumer);
        when(kafka.send(any(ProducerRecord.class))).thenReturn(CompletableFuture.completedFuture(null));

        EventEnvelope event = validEvent();
        String json = mapper.writeValueAsString(event);
        TopicPartition partition = new TopicPartition(topics.dlq(), 0);
        ConsumerRecord<String, String> record =
            new ConsumerRecord<>(topics.dlq(), 0, 5L, event.conversationId(), json);
        record.headers().add(DlqHeaders.ORIGINAL_TOPIC,
            "wa.message.received.v1".getBytes(StandardCharsets.UTF_8));
        record.headers().add("correlationId", event.correlationId().getBytes(StandardCharsets.UTF_8));
        record.headers().add("eventType", "MessageReceived".getBytes(StandardCharsets.UTF_8));
        record.headers().add("eventVersion", "1".getBytes(StandardCharsets.UTF_8));
        record.headers().add("kafka_dlt-exception-fqcn",
            "com.example.Transient".getBytes(StandardCharsets.UTF_8));
        record.headers().add(DlqHeaders.ATTEMPTS, "5".getBytes(StandardCharsets.UTF_8));
        when(consumer.poll(any(Duration.class)))
            .thenReturn(new ConsumerRecords<>(Map.of(partition, List.of(record))))
            .thenReturn(ConsumerRecords.empty());

        int count = new DlqReprocessor(factory, kafka, topics, envelopeReader).reprocess(5L, 5L);
        assertThat(count).isEqualTo(1);
        verify(consumer).assign(Collections.singleton(partition));
        verify(consumer).seek(partition, 5L);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<ProducerRecord<String, String>> sent = ArgumentCaptor.forClass(ProducerRecord.class);
        verify(kafka).send(sent.capture());
        assertThat(sent.getValue().topic()).isEqualTo("wa.message.received.v1");
        assertThat(sent.getValue().key()).isEqualTo(event.conversationId());
        assertThat(sent.getValue().value()).isEqualTo(json);
        assertThat(new String(sent.getValue().headers().lastHeader(DlqHeaders.REPROCESSED_FROM).value(),
            StandardCharsets.UTF_8)).isEqualTo("5");
        assertThat(sent.getValue().headers().lastHeader("correlationId")).isNotNull();
        assertThat(sent.getValue().headers().lastHeader("eventType")).isNotNull();
        assertThat(sent.getValue().headers().lastHeader("eventVersion")).isNotNull();
        assertThat(sent.getValue().headers().lastHeader("kafka_dlt-exception-fqcn")).isNull();
        assertThat(sent.getValue().headers().lastHeader(DlqHeaders.ATTEMPTS)).isNull();
    }

    @Test
    void rejectsInvalidPayloadWithoutPublishing() {
        @SuppressWarnings("unchecked")
        ConsumerFactory<String, String> factory = mock(ConsumerFactory.class);
        @SuppressWarnings("unchecked")
        Consumer<String, String> consumer = mock(Consumer.class);
        @SuppressWarnings("unchecked")
        KafkaTemplate<String, String> kafka = mock(KafkaTemplate.class);
        when(factory.createConsumer(eq("wa-event-platform-dlq-reprocess"), eq(null))).thenReturn(consumer);

        TopicPartition partition = new TopicPartition(topics.dlq(), 0);
        ConsumerRecord<String, String> record =
            new ConsumerRecord<>(topics.dlq(), 0, 7L, "conv-1", "{not-json");
        record.headers().add(DlqHeaders.ORIGINAL_TOPIC,
            "wa.message.received.v1".getBytes(StandardCharsets.UTF_8));
        when(consumer.poll(any(Duration.class)))
            .thenReturn(new ConsumerRecords<>(Map.of(partition, List.of(record))))
            .thenReturn(ConsumerRecords.empty());

        DlqReprocessor reprocessor = new DlqReprocessor(factory, kafka, topics, envelopeReader);
        assertThatThrownBy(() -> reprocessor.reprocess(7L, 7L))
            .isInstanceOf(SchemaValidationException.class);
        verify(kafka, never()).send(any(ProducerRecord.class));
    }

    @Test
    void rejectsInvalidRange() {
        @SuppressWarnings("unchecked")
        DlqReprocessor reprocessor = new DlqReprocessor(mock(ConsumerFactory.class), mock(KafkaTemplate.class),
            topics, envelopeReader);
        assertThatThrownBy(() -> reprocessor.reprocess(-1, 1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> reprocessor.reprocess(3, 2)).isInstanceOf(IllegalArgumentException.class);
    }

    private EventEnvelope validEvent() throws Exception {
        return new EventEnvelope(UUID.randomUUID(), EventType.MessageReceived, 1, Instant.now(),
            "3b0f8e14-7c2a-4d55-9e01-11b22c33d44e",
            "conv-7d2f4a90-1c3b-5e6f-8a7b-9c0d1e2f3a4b", "SM-dlq-reprocess",
            mapper.readTree("{\"channel\":\"whatsapp\",\"from\":\"+12025550123\","
                + "\"to\":\"+12025550124\",\"kind\":\"TEXT\",\"text\":{\"body\":\"Hola\"}}"));
    }
}
