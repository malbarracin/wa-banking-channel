package com.bcra.gateway.messaging.producer;

import com.bcra.gateway.normalization.StatusNormalizer;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class StatusEventPublisherTest {
    @Test
    @SuppressWarnings("unchecked")
    void validatesSchemaAndWaitsForKafkaAckWithContractHeaders() throws Exception {
        KafkaTemplate<String, String> kafka = mock(KafkaTemplate.class);
        var ack = new CompletableFuture<SendResult<String, String>>();
        when(kafka.send(any(ProducerRecord.class))).thenReturn(ack);
        var mapper = new ObjectMapper();
        var normalizer = new StatusNormalizer(mapper);
        var form = Map.of("MessageSid", "SM00000000000000000000000000000000", "MessageStatus", "failed",
                "From", "whatsapp:+15005550006", "To", "whatsapp:+15005550007", "ErrorCode", "30003");
        var event = normalizer.normalize(form, UUID.randomUUID().toString());
        var publisher = new StatusEventPublisher(kafka, mapper);
        var completion = publisher.publish(event).toCompletableFuture();
        assertThat(completion).isNotDone();
        ArgumentCaptor<ProducerRecord<String, String>> record = ArgumentCaptor.forClass(ProducerRecord.class);
        verify(kafka).send(record.capture());
        assertThat(record.getValue().topic()).isEqualTo("wa.message.status.v1");
        assertThat(record.getValue().key()).isEqualTo(event.path("conversationId").asText());
        assertThat(record.getValue().value()).doesNotContain("whatsapp:").contains("30003");
        for (String header : new String[] {"correlationId", "eventType", "eventVersion"}) {
            assertThat(new String(record.getValue().headers().lastHeader(header).value(), StandardCharsets.UTF_8))
                    .isEqualTo(event.path(header).asText());
        }
        ack.complete(null);
        assertThat(completion).isCompleted();
        event.put("extra", "forbidden");
        assertThatThrownBy(() -> publisher.publish(event)).isInstanceOf(IllegalArgumentException.class);
        verifyNoMoreInteractions(kafka);
        var invalid = new java.util.HashMap<>(form);
        invalid.put("ErrorCode", "sensitive details");
        assertThatThrownBy(() -> normalizer.normalize(invalid, UUID.randomUUID().toString()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
