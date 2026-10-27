package com.bcra.events.messaging.schema;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SchemaValidatorsConfig;
import com.networknt.schema.SpecVersion;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class EventSchemaValidator {
    private final ObjectMapper mapper;
    private final Map<String, JsonSchema> schemas = new HashMap<>();

    public EventSchemaValidator(ObjectMapper mapper) throws IOException {
        this.mapper = mapper;
        var factory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7);
        var config = SchemaValidatorsConfig.builder().formatAssertionsEnabled(true).build();
        for (String name : new String[] {"received", "ready-to-send", "status", "dlq"}) {
            String topic = "wa.message." + name + ".v1";
            try (InputStream input = getClass().getResourceAsStream("/contracts/events/" + topic + ".schema.json")) {
                if (input == null) {
                    throw new IOException("Missing event schema: " + topic);
                }
                schemas.put(topic, factory.getSchema(mapper.readTree(input), config));
            }
        }
    }

    public void validate(String topic, String value) {
        JsonSchema schema = schemas.get(topic);
        if (schema == null) {
            throw new InvalidEventException();
        }
        try {
            JsonNode event = mapper.readTree(value);
            if (event == null || !schema.validate(event).isEmpty()) {
                throw new InvalidEventException();
            }
        } catch (IOException | IllegalArgumentException exception) {
            throw new InvalidEventException();
        }
    }
}
