package com.bcra.orchestrator.messaging.consumer;

import com.bcra.orchestrator.dedup.DedupeService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.slf4j.MDC;
import org.springframework.kafka.support.Acknowledgment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class InboundEventConsumerTest {
    static ConsumerRecord<String, String> record(String messageId, String conversationId) {
        var mapper = new ObjectMapper();
        var event = mapper.createObjectNode();
        event.put("eventId", UUID.randomUUID().toString());
        event.put("eventType", "MessageReceived");
        event.put("eventVersion", 1);
        event.put("occurredAt", "2026-10-28T10:00:00Z");
        event.put("correlationId", UUID.randomUUID().toString());
        event.put("conversationId", conversationId);
        event.put("messageId", messageId);
        event.putObject("payload").put("customerAddress", "whatsapp:+15005550006").put("body", "hola");
        var record = new ConsumerRecord<String, String>("wa.message.received.v1", 0, 0,
                conversationId, event.toString());
        for (String name : new String[]{"correlationId", "eventType", "eventVersion"}) {
            record.headers().add(name, event.path(name).asText().getBytes(StandardCharsets.UTF_8));
        }
        return record;
    }

    @Test
    @DisplayName("Confirma después del efecto y restaura la correlación previa")
    void acknowledgesOnlyAfterEffectAndRestoresCorrelation() {
        var dedupe = mock(DedupeService.class);
        var validator = new InboundEventValidator(new ObjectMapper());
        var record = record("one", "conversation");
        var message = validator.validate(record);
        var ack = mock(Acknowledgment.class);
        when(dedupe.process(message)).thenAnswer(invocation -> {
            assertThat(MDC.get("correlationId")).isEqualTo(message.correlationId().toString());
            return true;
        });
        MDC.put("correlationId", "previous");
        try {
            new InboundEventConsumer(validator, dedupe).consume(record, ack);
            var order = inOrder(dedupe, ack);
            order.verify(dedupe).process(message);
            order.verify(ack).acknowledge();
            assertThat(MDC.get("correlationId")).isEqualTo("previous");
        } finally {
            MDC.clear();
        }
        assertThat(message.toString()).doesNotContain("hola", "+15005550006");
    }

    @Test
    @DisplayName("Confirma duplicados pero no confirma un efecto fallido")
    void acknowledgesDuplicateButNeverAcknowledgesFailedEffect() {
        var dedupe = mock(DedupeService.class);
        var consumer = new InboundEventConsumer(new InboundEventValidator(new ObjectMapper()), dedupe);
        var ack = mock(Acknowledgment.class);
        consumer.consume(record("one", "conversation"), ack);
        verify(ack).acknowledge();
        reset(ack);
        when(dedupe.process(any())).thenThrow(new IllegalStateException("test failure"));
        assertThatThrownBy(() -> consumer.consume(record("two", "conversation"), ack))
                .isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(ack);
        assertThat(MDC.get("correlationId")).isNull();
    }

    @Test
    @DisplayName("Rechaza JSON, claves y headers inválidos antes de procesar")
    void rejectsMalformedSchemaKeyAndHeadersBeforeAnyEffect() {
        var dedupe = mock(DedupeService.class);
        var consumer = new InboundEventConsumer(new InboundEventValidator(new ObjectMapper()), dedupe);
        var ack = mock(Acknowledgment.class);
        for (String invalid : new String[]{"{", "null", "{}", "", "[]"}) {
            assertThatThrownBy(() -> consumer.consume(new ConsumerRecord<>("in", 0, 0, "key", invalid), ack))
                    .isInstanceOf(InvalidEventException.class);
        }
        assertThatThrownBy(() -> consumer.consume(new ConsumerRecord<>("in", 0, 0, "key", null), ack))
                .isInstanceOf(InvalidEventException.class);
        var valid = record("one", "conversation");
        assertThatThrownBy(() -> consumer.consume(new ConsumerRecord<>("in", 0, 0, "wrong", valid.value()), ack))
                .isInstanceOf(InvalidEventException.class);
        valid.headers().remove("eventVersion");
        assertThatThrownBy(() -> consumer.consume(valid, ack)).isInstanceOf(InvalidEventException.class);
        valid.headers().add("eventVersion", null);
        assertThatThrownBy(() -> consumer.consume(valid, ack)).isInstanceOf(InvalidEventException.class);
        valid.headers().remove("eventVersion");
        valid.headers().add("eventVersion", new byte[]{'2'});
        assertThatThrownBy(() -> consumer.consume(valid, ack)).isInstanceOf(InvalidEventException.class);
        verifyNoInteractions(dedupe, ack);
    }

    @Test
    @DisplayName("Valida formatos, campos obligatorios, propiedades adicionales y JSON completo")
    void enforcesStrictSharedSchema() throws Exception {
        var validator = new InboundEventValidator(new ObjectMapper());
        var mapper = new ObjectMapper();
        for (String field : new String[]{"eventId", "correlationId", "occurredAt", "payload"}) {
            var valid = record("one", "conversation");
            var event = (com.fasterxml.jackson.databind.node.ObjectNode) mapper.readTree(valid.value());
            event.put(field, "invalid");
            var invalid = new ConsumerRecord<String, String>(valid.topic(), 0, 0, valid.key(), event.toString());
            valid.headers().forEach(invalid.headers()::add);
            assertThatThrownBy(() -> validator.validate(invalid)).isInstanceOf(InvalidEventException.class);
        }
        var valid = record("one", "conversation");
        for (String json : new String[]{valid.value() + " {}",
                valid.value().replace("\"body\":\"hola\"", "\"body\":\"hola\",\"extra\":true"),
                valid.value().replace("\"messageId\":\"one\",", "")}) {
            var invalid = new ConsumerRecord<String, String>(valid.topic(), 0, 0, valid.key(), json);
            valid.headers().forEach(invalid.headers()::add);
            assertThatThrownBy(() -> validator.validate(invalid)).isInstanceOf(InvalidEventException.class);
        }
    }
}
