package com.bcra.users.ws.docs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Verifica que el contrato OpenAPI expuesto documente el formato único de error
 * y las respuestas que cada endpoint puede devolver.
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:openapi-contract;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OpenApiContractTest {

    private static final String ERROR_SCHEMA_REF = "#/components/schemas/ErrorResponse";

    private static final Map<String, List<String>> EXPECTED_RESPONSES = Map.of(
            "post /api/v1/users", List.of("201", "400", "409", "500"),
            "get /api/v1/users/{id}", List.of("200", "400", "404", "500"),
            "patch /api/v1/users/{id}", List.of("200", "400", "404", "500"),
            "patch /api/v1/users/{id}/status", List.of("200", "400", "404", "409", "500"),
            "get /api/v1/users/{id}/audit", List.of("200", "400", "500"));

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper objectMapper;

    private JsonNode root;

    @BeforeEach
    void loadApiDocs() throws Exception {
        String json = mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        root = objectMapper.readTree(json);
    }

    @Test
    @DisplayName("ErrorResponse exige code, message, details y timestamp")
    void errorResponseSchemaHasTheFourRequiredFields() {
        JsonNode schema = root.path("components").path("schemas").path("ErrorResponse");

        assertThat(schema.path("properties").fieldNames()).toIterable()
                .containsExactlyInAnyOrder("code", "message", "details", "timestamp");
        assertThat(schema.path("required")).extracting(JsonNode::asText)
                .containsExactlyInAnyOrder("code", "message", "details", "timestamp");
        assertThat(schema.path("properties").path("timestamp").path("format").asText()).isEqualTo("date-time");
        assertThat(schema.path("properties").path("code").path("enum")).extracting(JsonNode::asText)
                .containsExactlyInAnyOrder("VALIDATION_ERROR", "BAD_REQUEST", "NOT_FOUND", "CONFLICT",
                        "INTERNAL_ERROR");
    }

    @Test
    @DisplayName("Los ejemplos de error reutilizables son objetos con el formato único de error")
    void reusableErrorExamplesAreErrorObjects() {
        JsonNode examples = root.path("components").path("examples");

        assertThat(examples.size()).isPositive();
        examples.fields().forEachRemaining(example -> {
            JsonNode value = example.getValue().path("value");
            assertThat(value.isObject()).as("ejemplo %s debe ser un objeto JSON", example.getKey()).isTrue();
            assertThat(value.fieldNames()).toIterable()
                    .as("campos del ejemplo %s", example.getKey())
                    .containsExactlyInAnyOrder("code", "message", "details", "timestamp");
        });
    }

    @Test
    @DisplayName("Cada endpoint documenta sus respuestas de éxito y de error")
    void everyOperationDocumentsItsResponses() {
        EXPECTED_RESPONSES.forEach((operation, codes) -> {
            JsonNode responses = operation(operation).path("responses");
            assertThat(responses.fieldNames()).toIterable()
                    .as("respuestas de %s", operation)
                    .containsAll(codes);
        });
    }

    @Test
    @DisplayName("Toda respuesta de error usa el esquema ErrorResponse con un ejemplo")
    void everyErrorResponseReferencesTheErrorSchema() {
        List<String> checked = new ArrayList<>();
        EXPECTED_RESPONSES.keySet().forEach(operation -> operation(operation).path("responses").fields()
                .forEachRemaining(response -> {
                    if (!response.getKey().startsWith("4") && !response.getKey().startsWith("5")) {
                        return;
                    }
                    JsonNode media = response.getValue().path("content").elements().next();
                    assertThat(media.path("schema").path("$ref").asText())
                            .as("%s %s", operation, response.getKey())
                            .isEqualTo(ERROR_SCHEMA_REF);
                    boolean hasExamples = media.path("examples").size() > 0;
                    boolean hasExample = media.hasNonNull("example");
                    assertThat(hasExamples || hasExample)
                            .as("ejemplo no nulo en %s %s", operation, response.getKey())
                            .isTrue();
                    checked.add(operation + " " + response.getKey());
                }));

        assertThat(checked).hasSizeGreaterThanOrEqualTo(15);
    }

    @Test
    @DisplayName("Los cuerpos de alta, actualización y cambio de estado están documentados")
    void requestBodiesReferenceTheirSchemas() {
        assertThat(requestSchemaRef("post /api/v1/users")).endsWith("/CreateUserRequest");
        assertThat(requestSchemaRef("patch /api/v1/users/{id}")).endsWith("/UpdateUserRequest");
        assertThat(requestSchemaRef("patch /api/v1/users/{id}/status")).endsWith("/ChangeUserStatusRequest");
    }

    private JsonNode operation(String key) {
        String[] parts = key.split(" ", 2);
        JsonNode node = root.path("paths").path(parts[1]).path(parts[0]);
        assertThat(node.isMissingNode()).as("operación %s", key).isFalse();
        return node;
    }

    private String requestSchemaRef(String key) {
        JsonNode media = operation(key).path("requestBody").path("content").elements().next();
        return media.path("schema").path("$ref").asText();
    }
}
