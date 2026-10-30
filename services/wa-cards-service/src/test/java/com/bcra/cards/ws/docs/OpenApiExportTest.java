package com.bcra.cards.ws.docs;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Exporta el contrato publicado y detecta operaciones sin documentación. */
@SpringBootTest
@AutoConfigureMockMvc
class OpenApiExportTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper mapper;

    @Test
    @DisplayName("Exporta las seis operaciones con parámetros, ejemplos y errores del contrato")
    void exportaContratoCompleto() throws Exception {
        String json = mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        String yaml = mvc.perform(get("/v3/api-docs.yaml")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        JsonNode api = mapper.readTree(json);
        assertThat(io.swagger.v3.core.util.Yaml.mapper().readTree(yaml)).isEqualTo(api);
        assertThat(api.path("paths").size()).isEqualTo(6);
        api.path("paths").properties().forEach(path -> path.getValue().properties().forEach(entry -> {
            JsonNode operation = entry.getValue();
            assertThat(operation.path("summary").asText()).isNotBlank();
            assertThat(operation.path("description").asText()).isNotBlank();
            operation.path("parameters").forEach(parameter -> {
                assertThat(parameter.path("description").asText()).isNotBlank();
                assertThat(parameter.has("example")).isTrue();
            });
            for (String code : List.of("400", "401", "403", "500")) {
                assertThat(operation.path("responses").has(code)).isTrue();
            }
            if (path.getKey().contains("{tarjetaId}")) {
                assertThat(operation.path("responses").has("404")).isTrue();
            }
            if (path.getKey().contains("bloquear")) {
                assertThat(operation.path("responses").has("409")).isTrue();
            }
            if (entry.getKey().equals("post")) {
                assertThat(operation.at("/requestBody/content/application~1json/examples").size()).isPositive();
            }
            operation.path("responses").properties().forEach(response -> {
                if (response.getValue().has("$ref")) {
                    JsonNode resolved = api.at(response.getValue().path("$ref").asText().substring(1));
                    assertThat(resolved.at("/content/application~1json/schema/$ref").asText())
                            .isEqualTo("#/components/schemas/ErrorResponse");
                    resolved.at("/content/application~1json/examples").forEach(example -> {
                        JsonNode value = api.at(example.path("$ref").asText().substring(1)).path("value");
                        assertThat(value.path("code").asText()).isNotBlank();
                        assertThat(value.path("details").isArray()).isTrue();
                    });
                }
                if (response.getKey().startsWith("2")) {
                    assertThat(response.getValue().at("/content/*~1*/examples").size()
                            + response.getValue().at("/content/application~1json/examples").size()).isPositive();
                }
            });
        }));
        assertThat(api.at("/components/schemas/ErrorResponse/properties/details").isMissingNode()).isFalse();
        assertThat(api.at("/components/schemas/FieldError/properties/field").isMissingNode()).isFalse();
        Path directory = Path.of("delivery", "openapi");
        Files.createDirectories(directory);
        Files.writeString(directory.resolve("openapi.json"), api.toPrettyString(), StandardCharsets.UTF_8);
        Files.writeString(directory.resolve("openapi.yaml"), yaml, StandardCharsets.UTF_8);
        mvc.perform(get("/swagger-ui.html")).andExpect(status().is3xxRedirection());
    }
}
