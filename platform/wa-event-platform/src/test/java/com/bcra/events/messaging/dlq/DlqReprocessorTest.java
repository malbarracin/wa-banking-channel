package com.bcra.events.messaging.dlq;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bcra.events.config.TopicsProperties;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Collections;
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
    private final TopicsProperties topics = new TopicsProperties(
        "wa.message.received.v1", "wa.message.ready-to-send.v1", "wa.message.status.v1", "wa.message.dlq.v1",
        new TopicsProperties.ConsumerGroups("g", "o", "r"));

    @Test
    void republishesOriginalKeyValueToOriginalTopic() {
        @SuppressWarnings("unchecked")
        ConsumerFactory<String, String> factory = mock(ConsumerFactory.class);
        @SuppressWarnings("unchecked")
        Consumer<String, String> consumer = mock(Consumer.class);
        @SuppressWarnings("unchecked")
        KafkaTemplate<String, String> kafka = mock(KafkaTemplate.class);
        when(factory.createConsumer(eq("wa-event-platform-dlq-reprocess"), eq(null))).thenReturn(consumer);
        when(kafka.send(any(ProducerRecord.class))).thenReturn(CompletableFuture.completedFuture(null));

        TopicPartition partition = new TopicPartition(topics.dlq(), 0);
        ConsumerRecord<String, String> record =
            new ConsumerRecord<>(topics.dlq(), 0, 5L, "conv-1", "{\"eventId\":\"x\"}");
        record.headers().add(DlqHeaders.ORIGINAL_TOPIC,
            "wa.message.received.v1".getBytes(StandardCharsets.UTF_8));
        when(consumer.poll(any(Duration.class)))
            .thenReturn(new ConsumerRecords<>(Map.of(partition, List.of(record))))
            .thenReturn(ConsumerRecords.empty());

        int count = new DlqReprocessor(factory, kafka, topics).reprocess(5L, 5L);
        assertThat(count).isEqualTo(1);
        verify(consumer).assign(Collections.singleton(partition));
        verify(consumer).seek(partition, 5L);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<ProducerRecord<String, String>> sent = ArgumentCaptor.forClass(ProducerRecord.class);
        verify(kafka).send(sent.capture());
        assertThat(sent.getValue().topic()).isEqualTo("wa.message.received.v1");
        assertThat(sent.getValue().key()).isEqualTo("conv-1");
        assertThat(sent.getValue().value()).isEqualTo("{\"eventId\":\"x\"}");
        assertThat(new String(sent.getValue().headers().lastHeader(DlqHeaders.REPROCESSED_FROM).value(),
            StandardCharsets.UTF_8)).isEqualTo("5");
    }

    @Test
    void rejectsInvalidRange() {
        @SuppressWarnings("unchecked")
        DlqReprocessor reprocessor = new DlqReprocessor(mock(ConsumerFactory.class), mock(KafkaTemplate.class), topics);
        assertThatThrownBy(() -> reprocessor.reprocess(-1, 1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> reprocessor.reprocess(3, 2)).isInstanceOf(IllegalArgumentException.class);
    }
}
