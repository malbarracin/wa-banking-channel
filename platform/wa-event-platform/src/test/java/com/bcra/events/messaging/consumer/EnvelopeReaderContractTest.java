package com.bcra.events.messaging.consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bcra.events.domain.EventEnvelope;
import com.bcra.events.domain.EventType;
import com.bcra.events.schema.SchemaCatalog;
import com.bcra.events.schema.SchemaValidationException;
import com.bcra.events.schema.SchemaValidator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;

/**
 * Contract failures the consumer must never retry. All of them have to surface as
 * {@link SchemaValidationException}, which is the type {@code DlqConfig} registers as not retryable,
 * so these records reach the DLQ on the first delivery.
 */
class EnvelopeReaderContractTest {
    private static final String RECEIVED_V1 = "wa.message.received.v1";
    private static final String CORRELATION = "3b0f8e14-7c2a-4d55-9e01-11b22c33d44e";
    private static final String CONVERSATION = "conv-7d2f4a90-1c3b-5e6f-8a7b-9c0d1e2f3a4b";
    private static final String PAYLOAD = "{\"channel\":\"whatsapp\",\"from\":\"+12025550123\","
        + "\"to\":\"+12025550124\",\"kind\":\"TEXT\",\"text\":{\"body\":\"Hola\"}}";

    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules()
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    private final EnvelopeReader reader = new EnvelopeReader(mapper, new SchemaValidator(new SchemaCatalog()));

    private String envelopeJson(int eventVersion, EventType type) throws Exception {
        return mapper.writeValueAsString(new EventEnvelope(UUID.randomUUID(), type, eventVersion, Instant.now(),
            CORRELATION, CONVERSATION, "SM" + UUID.randomUUID(), mapper.readTree(PAYLOAD)));
    }

    private ConsumerRecord<String, String> record(String topic, String key, String value) {
        ConsumerRecord<String, String> record = new ConsumerRecord<>(topic, 0, 0L, key, value);
        record.headers().add("correlationId", CORRELATION.getBytes(StandardCharsets.UTF_8));
        record.headers().add("eventType", "MessageReceived".getBytes(StandardCharsets.UTF_8));
        record.headers().add("eventVersion", "1".getBytes(StandardCharsets.UTF_8));
        return record;
    }

    private void expectContractFailure(ConsumerRecord<String, String> record) {
        assertThatThrownBy(() -> reader.read(record, event -> { }))
            .isInstanceOf(SchemaValidationException.class);
    }

    /** Positive control: without it every assertion below could pass for the wrong reason. */
    @Test
    void wellFormedRecordIsDelivered() throws Exception {
        AtomicInteger delivered = new AtomicInteger();
        reader.read(record(RECEIVED_V1, CONVERSATION, envelopeJson(1, EventType.MessageReceived)),
            event -> {
                assertThat(event.conversationId()).isEqualTo(CONVERSATION);
                assertThat(event.correlationId()).isEqualTo(CORRELATION);
                delivered.incrementAndGet();
            });
        assertThat(delivered).hasValue(1);
    }

    @Test
    void unparseableOrNonObjectJsonIsAContractFailure() {
        expectContractFailure(record(RECEIVED_V1, CONVERSATION, "{not-json"));
        expectContractFailure(record(RECEIVED_V1, CONVERSATION, "[]"));
        expectContractFailure(record(RECEIVED_V1, CONVERSATION, "null"));
        expectContractFailure(record(RECEIVED_V1, CONVERSATION, "7"));
    }

    @Test
    void payloadThatDoesNotMatchTheSchemaIsAContractFailure() throws Exception {
        String missingPayload = mapper.writeValueAsString(new EventEnvelope(UUID.randomUUID(),
            EventType.MessageReceived, 1, Instant.now(), CORRELATION, CONVERSATION, "SM1",
            mapper.createObjectNode()));
        expectContractFailure(record(RECEIVED_V1, CONVERSATION, missingPayload));
    }

    @Test
    void unknownEnvelopeFieldIsAContractFailure() throws Exception {
        var json = mapper.readTree(envelopeJson(1, EventType.MessageReceived));
        ((com.fasterxml.jackson.databind.node.ObjectNode) json).put("sobrante", "x");
        expectContractFailure(record(RECEIVED_V1, CONVERSATION, mapper.writeValueAsString(json)));
    }

    @Test
    void headersThatDisagreeWithTheEnvelopeAreAContractFailure() throws Exception {
        String value = envelopeJson(1, EventType.MessageReceived);

        ConsumerRecord<String, String> otherCorrelation = record(RECEIVED_V1, CONVERSATION, value);
        otherCorrelation.headers().add("correlationId",
            "00000000-0000-4000-8000-000000000000".getBytes(StandardCharsets.UTF_8));
        expectContractFailure(otherCorrelation);

        ConsumerRecord<String, String> otherType = record(RECEIVED_V1, CONVERSATION, value);
        otherType.headers().add("eventType", "MessageStatus".getBytes(StandardCharsets.UTF_8));
        expectContractFailure(otherType);

        ConsumerRecord<String, String> otherVersion = record(RECEIVED_V1, CONVERSATION, value);
        otherVersion.headers().add("eventVersion", "2".getBytes(StandardCharsets.UTF_8));
        expectContractFailure(otherVersion);
    }

    @Test
    void missingContractHeaderIsAContractFailure() throws Exception {
        String value = envelopeJson(1, EventType.MessageReceived);
        for (String omitted : new String[] {"correlationId", "eventType", "eventVersion"}) {
            ConsumerRecord<String, String> record = record(RECEIVED_V1, CONVERSATION, value);
            record.headers().remove(omitted);
            expectContractFailure(record);
        }
    }

    @Test
    void recordKeyMustBeTheConversationId() throws Exception {
        expectContractFailure(record(RECEIVED_V1, "conv-otra", envelopeJson(1, EventType.MessageReceived)));
        expectContractFailure(record(RECEIVED_V1, null, envelopeJson(1, EventType.MessageReceived)));
    }

    @Test
    void eventVersionMustMatchTheTopicSuffix() throws Exception {
        // Valid v1 envelope delivered on the v2 topic: the suffix no longer matches the envelope.
        expectContractFailure(record("wa.message.received.v2", CONVERSATION,
            envelopeJson(1, EventType.MessageReceived)));
        // Envelope announcing a version the catalog does not publish.
        expectContractFailure(record(RECEIVED_V1, CONVERSATION, envelopeJson(2, EventType.MessageReceived)));
    }

    @Test
    void eventTypeMustMatchItsOwnTopic() throws Exception {
        ConsumerRecord<String, String> wrongTopic = new ConsumerRecord<>("wa.message.status.v1", 0, 0L,
            CONVERSATION, envelopeJson(1, EventType.MessageReceived));
        wrongTopic.headers().add("correlationId", CORRELATION.getBytes(StandardCharsets.UTF_8));
        wrongTopic.headers().add("eventType", "MessageReceived".getBytes(StandardCharsets.UTF_8));
        wrongTopic.headers().add("eventVersion", "1".getBytes(StandardCharsets.UTF_8));
        expectContractFailure(wrongTopic);
    }
}
