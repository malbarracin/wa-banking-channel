package com.bcra.channel.ws.docs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
class OpenApiExportTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void exportsTheActualApiContract() throws Exception {
        String json = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        String yaml = mockMvc.perform(get("/v3/api-docs.yaml"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        JsonNode document = new ObjectMapper().readTree(json);
        JsonNode paths = document.path("paths");
        assertThat(paths.size()).isGreaterThan(0);
        paths.fields().forEachRemaining(path -> path.getValue().fields().forEachRemaining(operation ->
                operation.getValue().path("responses").fields().forEachRemaining(response -> {
                    if (response.getKey().matches("[45]\\d\\d")) {
                        JsonNode media = response.getValue().path("content").path("application/json");
                        JsonNode examples = media.path("examples");
                        assertThat(examples.isObject() && !examples.isEmpty())
                                .as("error examples for %s %s %s", operation.getKey(), path.getKey(), response.getKey())
                                .isTrue();
                        examples.elements().forEachRemaining(example -> {
                            String reference = example.path("$ref").asText();
                            assertThat(reference).startsWith("#/components/examples/");
                            JsonNode value = document.at(reference.substring(1)).path("value");
                            assertThat(value.isObject()).as("resolved example %s", reference).isTrue();
                            assertThat(value.path("code").asText()).isNotBlank();
                            assertThat(value.path("message").asText()).isNotBlank();
                            assertThat(value.path("details").isArray()).isTrue();
                            assertThat(value.path("timestamp").asText()).isNotBlank();
                        });
                    }
                })));
        Path delivery = Path.of("delivery", "openapi");
        Path shared = Path.of("..", "..", "shared", "contracts", "wa-channel-service");
        Files.createDirectories(delivery);
        Files.createDirectories(shared);
        Files.writeString(delivery.resolve("openapi.json"), json, StandardCharsets.UTF_8);
        Files.writeString(delivery.resolve("openapi.yaml"), yaml, StandardCharsets.UTF_8);
        Files.writeString(shared.resolve("openapi.yaml"), yaml, StandardCharsets.UTF_8);
    }
}
