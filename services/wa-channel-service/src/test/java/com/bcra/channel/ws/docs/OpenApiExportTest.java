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
        assertThat(new ObjectMapper().readTree(json).path("paths").size()).isGreaterThan(0);
        Path delivery = Path.of("delivery", "openapi");
        Path shared = Path.of("..", "..", "shared", "contracts", "wa-channel-service");
        Files.createDirectories(delivery);
        Files.createDirectories(shared);
        Files.writeString(delivery.resolve("openapi.json"), json, StandardCharsets.UTF_8);
        Files.writeString(delivery.resolve("openapi.yaml"), yaml, StandardCharsets.UTF_8);
        Files.writeString(shared.resolve("openapi.yaml"), yaml, StandardCharsets.UTF_8);
    }
}
