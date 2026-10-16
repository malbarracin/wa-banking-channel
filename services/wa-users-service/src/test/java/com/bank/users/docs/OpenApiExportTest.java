package com.bank.users.docs;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Regenera {@code delivery/openapi/} en cada {@code verify} a partir de {@code /v3/api-docs}.
 */
@SpringBootTest
@AutoConfigureMockMvc
class OpenApiExportTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("Exporta OpenAPI JSON y YAML con los cinco endpoints de usuarios")
    void exportaLaEspecificacionOpenApi() throws Exception {
        String json = mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        String yaml = mvc.perform(get("/v3/api-docs.yaml"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        Path dir = Path.of("delivery", "openapi");
        Files.createDirectories(dir);
        JsonNode tree = objectMapper.readTree(json);
        Files.writeString(dir.resolve("openapi.json"), tree.toPrettyString());
        Files.writeString(dir.resolve("openapi.yaml"), yaml);

        JsonNode paths = tree.path("paths");
        assertThat(paths.path("/api/v1/users").path("post").isObject()).isTrue();
        assertThat(paths.path("/api/v1/users/{id}").path("get").isObject()).isTrue();
        assertThat(paths.path("/api/v1/users/{id}").path("patch").isObject()).isTrue();
        assertThat(paths.path("/api/v1/users/{id}/status").path("patch").isObject()).isTrue();
        assertThat(paths.path("/api/v1/users/{id}/channel-eligibility").path("get").isObject()).isTrue();
        assertThat(tree.path("components").path("examples").path("ValidationError").isObject()).isTrue();
        assertThat(tree.path("components").path("examples").path("NotFound").isObject()).isTrue();
        assertThat(tree.path("components").path("examples").path("ConflictDuplicate").isObject()).isTrue();
    }
}
