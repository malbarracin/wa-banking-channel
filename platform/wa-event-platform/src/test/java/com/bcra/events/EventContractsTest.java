package com.bcra.events;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EventContractsTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final JsonSchemaFactory FACTORY = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012,
        builder -> builder.schemaMappers(mappers -> mappers.mapPrefix(
            "https://contracts.bcra.local/wa/events/", "classpath:contracts/events/")));

    private JsonSchema schema(String name) {
        return FACTORY.getSchema(java.net.URI.create("https://contracts.bcra.local/wa/events/" + name + ".v1.schema.json"));
    }

    private ObjectNode event(String type, String payload) throws Exception {
        return (ObjectNode) MAPPER.readTree("""
            {"eventId":"9f1c1a2e-5b7d-4c3a-8f21-0a1b2c3d4e5f","eventType":"%s",
            "eventVersion":1,"occurredAt":"2026-11-21T14:03:12Z",
            "correlationId":"3b0f8e14-7c2a-4d55-9e01-11b22c33d44e",
            "conversationId":"conv-7d2f4a90-1c3b-5e6f-8a7b-9c0d1e2f3a4b","messageId":"SM123","payload":%s}
            """.formatted(type, payload));
    }

    @Test
    void receivedRequiresContentForKindAndRejectsUnknownFields() throws Exception {
        JsonSchema schema = schema("message-received");
        ObjectNode event = event("MessageReceived", """
            {"channel":"whatsapp","from":"+12025550123","to":"+12025550124","kind":"MEDIA",
            "media":{"providerMediaId":"media123","contentType":"image/jpeg"}}
            """);
        assertThat(schema.validate(event)).isEmpty();
        ((ObjectNode) event.get("payload")).remove("media");
        assertThat(schema.validate(event)).isNotEmpty();
        event.put("unexpected", true);
        assertThat(schema.validate(event)).isNotEmpty();
    }

    @Test
    void readyAndStatusEnforceTheirPayloads() throws Exception {
        JsonSchema ready = schema("message-ready-to-send");
        ObjectNode message = event("MessageReadyToSend", """
            {"to":"+12025550123","kind":"TEMPLATE","template":{"name":"welcome","language":"es_AR","variables":[]}}
            """);
        assertThat(ready.validate(message)).isEmpty();
        ((ObjectNode) message.get("payload")).remove("template");
        assertThat(ready.validate(message)).isNotEmpty();
        JsonSchema status = schema("message-status");
        ObjectNode update = event("MessageStatus", "{\"providerMessageId\":\"SM123\",\"status\":\"DELIVERED\"}");
        assertThat(status.validate(update)).isEmpty();
        ((ObjectNode) update.get("payload")).put("status", "UNKNOWN");
        assertThat(status.validate(update)).isNotEmpty();
    }
}
