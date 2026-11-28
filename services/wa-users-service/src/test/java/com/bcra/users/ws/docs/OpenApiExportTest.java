package com.bcra.users.ws.docs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Exporta la especificación OpenAPI desde /v3/api-docs al contrato publicado
 * y a delivery/openapi, para que coincida con el código en cada verify.
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:openapi-export;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OpenApiExportTest {

    private static final Set<String> REQUIRED_PATHS = Set.of(
            "/api/v1/users",
            "/api/v1/users/{id}",
            "/api/v1/users/{id}/status",
            "/api/v1/users/{id}/audit");

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("Exporta OpenAPI 3 al contrato publicado y verifica los cinco endpoints")
    void exportaLaEspecificacionOpenApi() throws Exception {
        String json = mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        String yaml = mvc.perform(get("/v3/api-docs.yaml"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);

        JsonNode root = objectMapper.readTree(json);
        assertThat(root.path("openapi").asText()).startsWith("3.");
        JsonNode paths = root.path("paths");
        assertThat(paths.fieldNames()).toIterable().containsAll(REQUIRED_PATHS);

        assertThat(paths.path("/api/v1/users").has("post")).isTrue();
        assertThat(paths.path("/api/v1/users/{id}").has("get")).isTrue();
        assertThat(paths.path("/api/v1/users/{id}").has("patch")).isTrue();
        assertThat(paths.path("/api/v1/users/{id}/status").has("patch")).isTrue();
        assertThat(paths.path("/api/v1/users/{id}/audit").has("get")).isTrue();

        assertErrorContractPresent(paths.path("/api/v1/users").path("post"));
        assertErrorContractPresent(paths.path("/api/v1/users/{id}").path("get"));
        assertErrorContractPresent(paths.path("/api/v1/users/{id}").path("patch"));
        assertErrorContractPresent(paths.path("/api/v1/users/{id}/status").path("patch"));
        assertErrorContractPresent(paths.path("/api/v1/users/{id}/audit").path("get"));

        Path contractDir = Path.of("..", "..", "shared", "contracts", "wa-users-service").normalize();
        Files.createDirectories(contractDir);
        Files.writeString(contractDir.resolve("openapi.yaml"), yaml, StandardCharsets.UTF_8);
        Files.writeString(contractDir.resolve("openapi.json"),
                objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(root),
                StandardCharsets.UTF_8);

        Path deliveryDir = Path.of("delivery", "openapi");
        Files.createDirectories(deliveryDir);
        Files.writeString(deliveryDir.resolve("openapi.yaml"), yaml, StandardCharsets.UTF_8);
        Files.writeString(deliveryDir.resolve("openapi.json"),
                objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(root),
                StandardCharsets.UTF_8);

        assertThat(contractDir.resolve("openapi.yaml")).exists();
        assertThat(Files.readString(contractDir.resolve("openapi.yaml"), StandardCharsets.UTF_8))
                .contains("openapi:")
                .contains("/api/v1/users")
                .doesNotContain("DATABASE_PASSWORD")
                .doesNotContain("password:");
    }

    private static void assertErrorContractPresent(JsonNode operation) {
        JsonNode responses = operation.path("responses");
        assertThat(responses.has("400") || responses.has("404") || responses.has("409") || responses.has("500"))
                .as("operation must document at least one error response")
                .isTrue();
        for (String code : List.of("400", "404", "409", "500")) {
            if (!responses.has(code)) {
                continue;
            }
            String body = responses.path(code).toString();
            assertThat(body).containsAnyOf("ErrorResponse", "code", "VALIDATION_ERROR", "BAD_REQUEST",
                    "NOT_FOUND", "CONFLICT", "INTERNAL_ERROR");
        }
    }
}
