package com.bcra.credentials.ws.docs;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Verifica y exporta el contrato que springdoc genera desde los endpoints reales de H2. */
@SpringBootTest(properties = "server.port=8082")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OpenApiExportTest {

    private static final String BASE_PATH = "/api/v1/credenciales";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("exporta JSON y YAML con los siete endpoints, ejemplos y errores reales de H2")
    void exportaContratoCompletoDeCredenciales() throws Exception {
        String json = consultar("/v3/api-docs");
        String yaml = consultar("/v3/api-docs.yaml");
        JsonNode document = objectMapper.readTree(json);
        JsonNode paths = document.path("paths");

        assertThat(document.path("info").path("title").asText()).isEqualTo("wa-credentials-service");
        assertThat(document.path("info").path("version").asText()).isEqualTo("0.1.0-SNAPSHOT");
        assertThat(document.path("servers").get(0).path("url").asText()).isEqualTo("http://localhost:8082");
        assertThat(paths.size()).isEqualTo(7);
        Map<String, List<String>> expected = Map.of(
                "/emitir", List.of("201", "400", "401", "403", "404", "409", "500", "503"),
                "/validar", List.of("200", "400", "401", "500"),
                "/{numero}/revocacion", List.of("204", "400", "401", "403", "404", "500"),
                "/revocacion-masiva", List.of("200", "400", "401", "403", "500"),
                "/{numero}/historial", List.of("200", "400", "404", "500"),
                "/renovacion/obtener", List.of("200", "400", "401", "404", "409", "500"),
                "/renovacion/confirmar", List.of("204", "400", "401", "409", "500"));

        for (Map.Entry<String, List<String>> entry : expected.entrySet()) {
            String method = entry.getKey().endsWith("historial") ? "get" : "post";
            JsonNode operation = paths.path(BASE_PATH + entry.getKey()).path(method);
            assertThat(operation.path("summary").asText()).isNotBlank();
            assertThat(operation.path("description").asText()).isNotBlank();
            assertThat(operation.path("tags").size()).isPositive();
            assertThat(operation.path("responses").properties().stream().map(Map.Entry::getKey).toList())
                    .containsExactlyInAnyOrderElementsOf(entry.getValue());
            if ("post".equals(method)) {
                assertThat(operation.path("requestBody").path("required").asBoolean()).isTrue();
                verificarEjemplos(operation.path("requestBody").path("content").path("application/json"), document);
            }
            for (JsonNode parameter : operation.path("parameters")) {
                assertThat(parameter.path("description").asText()).isNotBlank();
                assertThat(parameter.path("example").asText()).isEqualTo("+5491111111111");
            }
            for (Map.Entry<String, JsonNode> response : operation.path("responses").properties()) {
                if ("204".equals(response.getKey())) {
                    assertThat(response.getValue().has("content")).isFalse();
                } else {
                    verificarEjemplos(response.getValue().path("content").path("application/json"), document);
                }
            }
        }

        JsonNode schemas = document.path("components").path("schemas");
        for (String name : List.of("EmitirCredencialRequest", "EmitirCredencialResponse", "ValidarCredencialRequest",
                "ValidarCredencialResponse", "RevocarCredencialRequest", "RevocacionMasivaRequest", "Segmento",
                "RevocacionMasivaResponse", "HistorialCredencialResponse", "EventoCredencialResponse")) {
            assertThat(schemas.has(name)).as("schema %s", name).isTrue();
            for (JsonNode property : schemas.path(name).path("properties")) {
                assertThat(property.path("description").asText()).isNotBlank();
                assertThat(property.has("example")).isTrue();
            }
        }
        assertThat(schemas.path("RevocacionMasivaRequest").path("properties").has("alcanceValido")).isFalse();
        assertThat(schemas.path("ErrorResponse").path("properties").properties().stream()
                .map(Map.Entry::getKey).toList()).containsExactlyInAnyOrder("code", "message", "details", "timestamp");

        Path directory = Path.of("delivery", "openapi");
        Files.createDirectories(directory);
        Files.writeString(directory.resolve("openapi.json"), document.toPrettyString(), StandardCharsets.UTF_8);
        Files.writeString(directory.resolve("openapi.yaml"), yaml, StandardCharsets.UTF_8);
    }

    private String consultar(String path) throws Exception {
        return mockMvc.perform(get(path)).andExpect(status().isOk()).andReturn()
                .getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    private void verificarEjemplos(JsonNode content, JsonNode document) {
        assertThat(content.path("schema").isMissingNode()).isFalse();
        assertThat(content.path("examples").size()).isPositive();
        for (JsonNode example : content.path("examples")) {
            JsonNode resolved = example.has("$ref")
                    ? document.at(example.path("$ref").asText().substring(1)) : example;
            assertThat(resolved.has("value")).isTrue();
            if (example.has("$ref")) {
                JsonNode value = resolved.path("value");
                assertThat(value.path("code").asText()).isNotBlank();
                assertThat(value.path("message").asText()).isNotBlank();
                assertThat(value.path("details").isArray()).isTrue();
                assertThat(value.path("timestamp").asText()).isEqualTo("2026-10-26T12:00:00Z");
            }
        }
    }
}
