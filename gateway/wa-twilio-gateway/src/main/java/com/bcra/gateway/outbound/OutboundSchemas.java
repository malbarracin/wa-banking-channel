package com.bcra.gateway.outbound;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SchemaValidatorsConfig;
import com.networknt.schema.SpecVersion;

public class OutboundSchemas {
    public static final String READY = "wa.message.ready-to-send.v1";
    public static final String DLQ = "wa.message.dlq.v1";
    private final ObjectMapper mapper;
    private final Map<String, JsonSchema> schemas = new HashMap<>();

    public OutboundSchemas(ObjectMapper mapper) throws IOException {
        this.mapper = mapper;
        for (String topic : new String[] {READY, DLQ}) {
            try (var input = getClass().getResourceAsStream("/contracts/events/" + topic + ".schema.json")) {
                if (input == null) {
                    throw new IOException("Missing outbound schema");
                }
                schemas.put(topic, JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7)
                        .getSchema(mapper.readTree(input),
                                SchemaValidatorsConfig.builder().formatAssertionsEnabled(true).build()));
            }
        }
    }

    public JsonNode validate(String topic, String value) {
        try {
            JsonNode event = mapper.readTree(value);
            if (event == null || !schemas.get(topic).validate(event).isEmpty()) {
                throw new OutboundException(OutboundException.Kind.INVALID, "SCHEMA_VALIDATION_FAILED");
            }
            return event;
        } catch (JsonProcessingException | IllegalArgumentException exception) {
            throw new OutboundException(OutboundException.Kind.INVALID, "SCHEMA_VALIDATION_FAILED");
        }
    }
}
