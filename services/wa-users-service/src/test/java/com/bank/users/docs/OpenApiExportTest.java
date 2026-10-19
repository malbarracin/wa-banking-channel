package com.bank.users.docs;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Exporta la especificación OpenAPI ({@code openapi.json} y {@code openapi.yaml}) al directorio
 * {@code delivery/openapi/} durante el build, de modo que siempre coincide con el código.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class OpenApiExportTest {

    private final MockMvc mockMvc;
    private final ObjectMapper objectMapper;

    OpenApiExportTest(MockMvc mockMvc, ObjectMapper objectMapper) {
        this.mockMvc = mockMvc;
        this.objectMapper = objectMapper;
    }

    @Test
    @DisplayName("Genera openapi.json y openapi.yaml en delivery/openapi/ con todos los paths")
    void exportaLaEspecificacionOpenApi() throws Exception {
        String json = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        String yaml = mockMvc.perform(get("/v3/api-docs.yaml"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        Path directory = Path.of("delivery", "openapi");
        Files.createDirectories(directory);
        Files.writeString(directory.resolve("openapi.json"),
                objectMapper.readTree(json).toPrettyString());
        Files.writeString(directory.resolve("openapi.yaml"), yaml);

        JsonNode paths = objectMapper.readTree(json).path("paths");
        assertThat(paths.size()).isGreaterThan(0);
        for (var filename : new String[]{"openapi.json", "openapi.yaml"}) {
            assertThat(Files.readString(directory.resolve(filename))).isNotBlank();
        }
    }
}
