package com.bcra.events.messaging.dlq;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bcra.events.config.TopicsProperties;
import com.bcra.events.messaging.consumer.EnvelopeReader;
import com.bcra.events.schema.SchemaValidationException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
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
    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void validatesBeforeSendingAndPreservesRecord() throws Exception {
        ConsumerFactory<String, String> factory = mock(ConsumerFactory.class);
        Consumer<String, String> consumer = mock(Consumer.class);
        KafkaTemplate<String, String> producer = mock(KafkaTemplate.class);
        EnvelopeReader reader = mock(EnvelopeReader.class);
        TopicsProperties topics = new TopicsProperties("wa.message.received.v1", "wa.message.ready-to-send.v1",
            "wa.message.status.v1", "wa.message.dlq.v1", null);
        TopicPartition partition = new TopicPartition(topics.dlq(), 0);
        ConsumerRecord<String, String> record = new ConsumerRecord<>(topics.dlq(), 0, 7, "key", "value");
        record.headers().add(DlqHeaders.ORIGINAL_TOPIC, topics.received().getBytes(StandardCharsets.UTF_8));
        record.headers().add("correlationId", "correlation".getBytes(StandardCharsets.UTF_8));
        record.headers().add("eventType", "MessageReceived".getBytes(StandardCharsets.UTF_8));
        record.headers().add("eventVersion", "1".getBytes(StandardCharsets.UTF_8));
        when(factory.createConsumer()).thenReturn(consumer);
        when(consumer.beginningOffsets(any())).thenReturn(Map.of(partition, 0L));
        when(consumer.endOffsets(any())).thenReturn(Map.of(partition, 8L));
        when(consumer.position(partition)).thenReturn(7L, 8L);
        when(consumer.poll(any(java.time.Duration.class)))
            .thenReturn(new ConsumerRecords<>(Map.of(partition, List.of(record))));
        when(producer.send(any(ProducerRecord.class))).thenReturn(CompletableFuture.completedFuture(null));
        DlqReprocessor reprocessor = new DlqReprocessor(factory, producer, reader, topics);

        assertThat(reprocessor.reprocess(0, 7, 8)).isEqualTo(1);
        ArgumentCaptor<ProducerRecord> capture = ArgumentCaptor.forClass(ProducerRecord.class);
        verify(producer).send(capture.capture());
        assertThat(capture.getValue().topic()).isEqualTo(topics.received());
        assertThat(capture.getValue().key()).isEqualTo("key");
        assertThat(capture.getValue().value()).isEqualTo("value");
        assertThat(new String(capture.getValue().headers().lastHeader(DlqHeaders.REPROCESSED_FROM).value(),
            StandardCharsets.UTF_8)).isEqualTo("wa.message.dlq.v1:0:7");
        assertThat(header(capture.getValue(), "correlationId")).isEqualTo("correlation");
        assertThat(header(capture.getValue(), "eventType")).isEqualTo("MessageReceived");
        assertThat(header(capture.getValue(), "eventVersion")).isEqualTo("1");
        assertThat(capture.getValue().headers().lastHeader(DlqHeaders.ORIGINAL_TOPIC)).isNull();
        assertThat(capture.getValue().headers().lastHeader(DlqHeaders.EXCEPTION_FQCN)).isNull();
        verify(consumer, never()).commitSync();
        verify(consumer, never()).commitAsync();
        verify(consumer).close();
    }

    @Test
    @SuppressWarnings("unchecked")
    void rejectsRangesItMustNotTouchWithoutOpeningAConsumer() {
        ConsumerFactory<String, String> factory = mock(ConsumerFactory.class);
        KafkaTemplate<String, String> producer = mock(KafkaTemplate.class);
        DlqReprocessor reprocessor = new DlqReprocessor(factory, producer, mock(EnvelopeReader.class), topics());

        assertThatThrownBy(() -> reprocessor.reprocess(-1, 0, 1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> reprocessor.reprocess(0, -1, 5)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> reprocessor.reprocess(0, 5, 5)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> reprocessor.reprocess(0, 5, 4)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> reprocessor.reprocess(0, 0, 1_001)).isInstanceOf(IllegalArgumentException.class);
        verify(factory, never()).createConsumer();
    }

    @Test
    @SuppressWarnings("unchecked")
    void rejectsOffsetsOutsideTheRetainedRange() {
        ConsumerFactory<String, String> factory = mock(ConsumerFactory.class);
        Consumer<String, String> consumer = mock(Consumer.class);
        KafkaTemplate<String, String> producer = mock(KafkaTemplate.class);
        TopicsProperties topics = topics();
        TopicPartition partition = new TopicPartition(topics.dlq(), 0);
        when(factory.createConsumer()).thenReturn(consumer);
        when(consumer.beginningOffsets(any())).thenReturn(Map.of(partition, 10L));
        when(consumer.endOffsets(any())).thenReturn(Map.of(partition, 20L));
        DlqReprocessor reprocessor = new DlqReprocessor(factory, producer, mock(EnvelopeReader.class), topics);

        assertThatThrownBy(() -> reprocessor.reprocess(0, 5, 15))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("outside retained");
        assertThatThrownBy(() -> reprocessor.reprocess(0, 15, 25))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("outside retained");
        verify(producer, never()).send(any(ProducerRecord.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void refusesToRepublishOutsideTheBusinessTopics() {
        TopicsProperties topics = topics();
        assertThatThrownBy(() -> reprocessOne(topics, topics.dlq()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("not a business topic");
        assertThatThrownBy(() -> reprocessOne(topics, "wa.message.received.v2"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("not a business topic");
        assertThatThrownBy(() -> reprocessOne(topics, null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Missing original topic");
    }

    /** Runs one record whose {@code kafka_dlt-original-topic} header is {@code originalTopic}. */
    @SuppressWarnings("unchecked")
    private void reprocessOne(TopicsProperties topics, String originalTopic) throws Exception {
        ConsumerFactory<String, String> factory = mock(ConsumerFactory.class);
        Consumer<String, String> consumer = mock(Consumer.class);
        KafkaTemplate<String, String> producer = mock(KafkaTemplate.class);
        TopicPartition partition = new TopicPartition(topics.dlq(), 0);
        ConsumerRecord<String, String> record = new ConsumerRecord<>(topics.dlq(), 0, 0, "key", "value");
        if (originalTopic != null) {
            record.headers().add(DlqHeaders.ORIGINAL_TOPIC, originalTopic.getBytes(StandardCharsets.UTF_8));
        }
        when(factory.createConsumer()).thenReturn(consumer);
        when(consumer.beginningOffsets(any())).thenReturn(Map.of(partition, 0L));
        when(consumer.endOffsets(any())).thenReturn(Map.of(partition, 1L));
        when(consumer.position(partition)).thenReturn(0L, 1L);
        when(consumer.poll(any(java.time.Duration.class)))
            .thenReturn(new ConsumerRecords<>(Map.of(partition, List.of(record))));
        try {
            new DlqReprocessor(factory, producer, mock(EnvelopeReader.class), topics).reprocess(0, 0, 1);
        } finally {
            verify(producer, never()).send(any(ProducerRecord.class));
        }
    }

    private static TopicsProperties topics() {
        return new TopicsProperties("wa.message.received.v1", "wa.message.ready-to-send.v1",
            "wa.message.status.v1", "wa.message.dlq.v1", null);
    }

    private static String header(ProducerRecord<?, ?> record, String name) {
        return new String(record.headers().lastHeader(name).value(), StandardCharsets.UTF_8);
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void invalidSchemaStopsEntireRangeBeforePublishing() throws Exception {
        ConsumerFactory<String, String> factory = mock(ConsumerFactory.class);
        Consumer<String, String> consumer = mock(Consumer.class);
        KafkaTemplate<String, String> producer = mock(KafkaTemplate.class);
        EnvelopeReader reader = mock(EnvelopeReader.class);
        TopicsProperties topics = new TopicsProperties("wa.message.received.v1", "wa.message.ready-to-send.v1",
            "wa.message.status.v1", "wa.message.dlq.v1", null);
        TopicPartition partition = new TopicPartition(topics.dlq(), 0);
        ConsumerRecord<String, String> record = new ConsumerRecord<>(topics.dlq(), 0, 0, "key", "invalid");
        record.headers().add(DlqHeaders.ORIGINAL_TOPIC, topics.received().getBytes(StandardCharsets.UTF_8));
        when(factory.createConsumer()).thenReturn(consumer);
        when(consumer.beginningOffsets(any())).thenReturn(Map.of(partition, 0L));
        when(consumer.endOffsets(any())).thenReturn(Map.of(partition, 1L));
        when(consumer.position(partition)).thenReturn(0L, 1L);
        when(consumer.poll(any(java.time.Duration.class)))
            .thenReturn(new ConsumerRecords<>(Map.of(partition, List.of(record))));
        doThrow(new SchemaValidationException("invalid")).when(reader).read(any(), any());
        DlqReprocessor reprocessor = new DlqReprocessor(factory, producer, reader, topics);
        assertThatThrownBy(() -> reprocessor.reprocess(0, 0, 1)).isInstanceOf(SchemaValidationException.class);
        verify(producer, never()).send(any(ProducerRecord.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void rejectsInvalidPayloadWithoutPublishing() throws Exception {
        ConsumerFactory<String, String> factory = mock(ConsumerFactory.class);
        Consumer<String, String> consumer = mock(Consumer.class);
        KafkaTemplate<String, String> producer = mock(KafkaTemplate.class);
        EnvelopeReader reader = mock(EnvelopeReader.class);
        TopicsProperties topics = topics();
        TopicPartition partition = new TopicPartition(topics.dlq(), 0);
        ConsumerRecord<String, String> record = new ConsumerRecord<>(topics.dlq(), 0, 7L, "conv-1", "{not-json");
        record.headers().add(DlqHeaders.ORIGINAL_TOPIC, topics.received().getBytes(StandardCharsets.UTF_8));
        when(factory.createConsumer()).thenReturn(consumer);
        when(consumer.beginningOffsets(any())).thenReturn(Map.of(partition, 0L));
        when(consumer.endOffsets(any())).thenReturn(Map.of(partition, 8L));
        when(consumer.position(partition)).thenReturn(7L, 8L);
        when(consumer.poll(any(java.time.Duration.class)))
            .thenReturn(new ConsumerRecords<>(Map.of(partition, List.of(record))));
        doThrow(new SchemaValidationException("Invalid event JSON")).when(reader).read(any(), any());

        DlqReprocessor reprocessor = new DlqReprocessor(factory, producer, reader, topics);
        assertThatThrownBy(() -> reprocessor.reprocess(0, 7, 8)).isInstanceOf(SchemaValidationException.class);
        verify(producer, never()).send(any(ProducerRecord.class));
    }
}
