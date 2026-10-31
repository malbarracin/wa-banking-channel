package com.bcra.users.ws.docs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OpenApiExportTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("exporta OpenAPI a delivery/openapi y documenta los paths de usuarios")
    void exportaLaEspecificacionOpenApi() throws Exception {
        String json = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        String yaml = mockMvc.perform(get("/v3/api-docs.yaml"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);

        Path dir = Path.of("delivery", "openapi");
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("openapi.json"), objectMapper.readTree(json).toPrettyString());
        Files.writeString(dir.resolve("openapi.yaml"), yaml);

        JsonNode paths = objectMapper.readTree(json).path("paths");
        assertThat(paths.size()).isGreaterThan(0);
        assertThat(paths.has("/api/v1/users")).isTrue();
        assertThat(paths.has("/api/v1/users/{tipo}/{numero}")).isTrue();
        assertThat(paths.has("/api/v1/users/{tipo}/{numero}/status")).isTrue();
    }
}
