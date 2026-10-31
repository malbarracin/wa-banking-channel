package com.bcra.accounts.ws.docs;

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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Verifica Swagger público y exporta el contrato generado en cada build. */
@SpringBootTest
@AutoConfigureMockMvc
class OpenApiExportTest {

    private final MockMvc mockMvc;
    private final ObjectMapper objectMapper;

    @Autowired
    OpenApiExportTest(MockMvc mockMvc, ObjectMapper objectMapper) {
        this.mockMvc = mockMvc;
        this.objectMapper = objectMapper;
    }

    @Test
    @DisplayName("Swagger UI y su configuración son accesibles sin credencial")
    void permiteConsultarSwaggerSinCredencial() throws Exception {
        mockMvc.perform(get("/swagger-ui.html")).andExpect(status().is3xxRedirection());
        mockMvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
        mockMvc.perform(get("/v3/api-docs/swagger-config")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("Exporta los seis endpoints con parámetros, cuerpos y ejemplos de éxito y error")
    void exportaContratoCompleto() throws Exception {
        String json = mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        String yaml = mockMvc.perform(get("/v3/api-docs.yaml")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        JsonNode document = objectMapper.readTree(json);
        JsonNode paths = document.path("paths");
        assertThat(paths.size()).isEqualTo(6);
        for (String path : List.of("/api/v1/cuentas", "/api/v1/cuentas/{numeroCuenta}/saldo",
                "/api/v1/cuentas/{numero}/movimientos", "/api/v1/terceros",
                "/api/v1/transferencias", "/api/v1/transferencias/{id}/confirmar")) {
            assertThat(paths.has(path)).as(path).isTrue();
        }
        for (JsonNode operations : paths) {
            for (JsonNode operation : operations) {
                assertThat(operation.path("summary").asText()).isNotBlank();
                assertThat(operation.path("description").asText()).isNotBlank();
                assertThat(operation.path("responses").has("401")).isTrue();
                assertThat(operation.path("responses").has("500")).isTrue();
                for (JsonNode parameter : operation.path("parameters")) {
                    assertThat(parameter.path("description").asText()).isNotBlank();
                }
                for (JsonNode response : operation.path("responses")) {
                    JsonNode content = response.path("content").path("application/json");
                    assertThat(content.path("examples").size()).isPositive();
                    for (JsonNode example : content.path("examples")) {
                        String reference = example.path("$ref").asText();
                        assertThat(document.at(reference.substring(1)).isMissingNode()).isFalse();
                    }
                }
            }
        }
        JsonNode preparation = paths.path("/api/v1/transferencias").path("post");
        assertThat(preparation.path("requestBody").path("content").path("application/json")
                .path("examples").size()).isEqualTo(2);
        assertThat(preparation.path("responses").path("400").path("content")
                .path("application/json").path("examples").size()).isEqualTo(2);
        assertThat(document.path("components").path("schemas").has("ErrorResponse")).isTrue();
        assertThat(document.path("components").path("schemas").path("TransferenciaRequest")
                .path("properties").has("destinoValido")).isFalse();
        Path directory = Path.of("delivery", "openapi");
        Files.createDirectories(directory);
        Files.writeString(directory.resolve("openapi.json"), document.toPrettyString(), StandardCharsets.UTF_8);
        Files.writeString(directory.resolve("openapi.yaml"), yaml, StandardCharsets.UTF_8);
    }
}
