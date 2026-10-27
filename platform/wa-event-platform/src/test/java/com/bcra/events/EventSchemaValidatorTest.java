package com.bcra.events;

import com.bcra.events.messaging.schema.EventSchemaValidator;
import com.bcra.events.messaging.schema.InvalidEventException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EventSchemaValidatorTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final EventSchemaValidator validator = new EventSchemaValidator(mapper);

    EventSchemaValidatorTest() throws Exception {
    }

    @Test
    void validatesAllTopicsAndRejectsMissingFieldsAndUnknownProperties() throws Exception {
        String[] names = {"received", "ready-to-send", "status", "dlq"};
        String[] types = {"MessageReceived", "MessageReadyToSend", "MessageStatus", "DeadLetter"};
        String[] payloads = {
            "{\"customerAddress\":\"test-address\",\"body\":\"Hola\"}",
            "{\"customerAddress\":\"test-address\",\"body\":\"Hola\"}",
            "{\"status\":\"delivered\"}",
            "{\"originalTopic\":\"wa.message.received.v1\",\"originalPartition\":0,"
                + "\"originalOffset\":1,\"originalValueBase64\":\"e30=\","
                + "\"errorCode\":\"SCHEMA_VALIDATION_FAILED\"}"
        };
        for (int index = 0; index < names.length; index++) {
            String topic = "wa.message." + names[index] + ".v1";
            ObjectNode event = event(types[index], payloads[index]);
            assertThatCode(() -> validator.validate(topic, event.toString())).doesNotThrowAnyException();
            for (String field : new String[] {"eventId", "eventType", "eventVersion", "occurredAt",
                    "correlationId", "conversationId", "messageId", "payload"}) {
                ObjectNode missing = event.deepCopy();
                missing.remove(field);
                rejects(topic, missing.toString());
            }
            ObjectNode extra = event.deepCopy();
            extra.put("secret", "forbidden");
            rejects(topic, extra.toString());
            ((ObjectNode) event.get("payload")).put("unknown", true);
            rejects(topic, event.toString());
        }
    }

    @Test
    void rejectsMalformedJsonFormatsVersionsAndPayloadsWithoutExposingInput() throws Exception {
        String topic = "wa.message.received.v1";
        rejects(topic, "{private-data");
        rejects(topic, "null");
        rejects(topic, "");
        rejects("unknown", "{}");
        for (String field : new String[] {"eventId", "correlationId", "occurredAt", "eventType"}) {
            ObjectNode event = event("MessageReceived", "{\"customerAddress\":\"test\",\"body\":\"Hola\"}");
            event.put(field, "invalid");
            rejects(topic, event.toString());
        }
        ObjectNode event = event("MessageReceived", "{}");
        rejects(topic, event.toString());
        event.put("eventVersion", 2);
        rejects(topic, event.toString());
        rejects("wa.message.status.v1", event("MessageStatus", "{\"status\":\"unknown\"}").toString());
    }

    @Test
    void rejectsTrailingContentAfterValidEvent() throws Exception {
        String valid = event("MessageReceived", "{\"customerAddress\":\"test\",\"body\":\"Hola\"}").toString();
        rejects("wa.message.received.v1", valid + " {}");
        rejects("wa.message.received.v1", valid + " invalid");
    }

    @Test
    void enforcesPayloadAndIdentifierBoundaries() throws Exception {
        for (String field : new String[] {"conversationId", "messageId"}) {
            ObjectNode value = event("MessageReceived", "{\"customerAddress\":\"test\",\"body\":\"Hola\"}");
            value.put(field, "a".repeat(256));
            assertThatCode(() -> validator.validate("wa.message.received.v1", value.toString()))
                .doesNotThrowAnyException();
            value.put(field, "a".repeat(257));
            rejects("wa.message.received.v1", value.toString());
            value.put(field, "");
            rejects("wa.message.received.v1", value.toString());
        }
        ObjectNode value = event("DeadLetter", "{\"originalTopic\":\"wa.message.received.v1\","
            + "\"originalPartition\":0,\"originalOffset\":0,\"originalValueBase64\":\"e30=\","
            + "\"errorCode\":\"SCHEMA_VALIDATION_FAILED\"}");
        ObjectNode payload = (ObjectNode) value.get("payload");
        for (String field : new String[] {"originalPartition", "originalOffset"}) {
            ObjectNode invalid = value.deepCopy();
            ((ObjectNode) invalid.get("payload")).put(field, -1);
            rejects("wa.message.dlq.v1", invalid.toString());
        }
        payload.put("originalValueBase64", "not base64!");
        rejects("wa.message.dlq.v1", value.toString());
    }

    private ObjectNode event(String type, String payload) throws Exception {
        ObjectNode event = mapper.createObjectNode();
        event.put("eventId", "7bd3715a-72b5-4f7f-a6a4-f120ce339b5c");
        event.put("eventType", type);
        event.put("eventVersion", 1);
        event.put("occurredAt", "2026-10-27T12:00:00Z");
        event.put("correlationId", "7bd3715a-72b5-4f7f-a6a4-f120ce339b5c");
        event.put("conversationId", "test-conversation");
        event.put("messageId", "test-message");
        event.set("payload", mapper.readTree(payload));
        return event;
    }

    private void rejects(String topic, String value) {
        assertThatThrownBy(() -> validator.validate(topic, value))
            .isInstanceOf(InvalidEventException.class).hasMessage("SCHEMA_VALIDATION_FAILED");
    }
}
