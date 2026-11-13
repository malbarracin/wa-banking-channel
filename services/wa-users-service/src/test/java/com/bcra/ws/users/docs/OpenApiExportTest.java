package com.bcra.ws.users.docs;

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
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import com.bcra.ws.users.repository.UserAuditEventRepository;
import com.bcra.ws.users.repository.UserIndexInitializer;
import com.bcra.ws.users.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Exporta el contrato OpenAPI del grupo v1 a {@code delivery/openapi/} en cada verify.
 */
@SpringBootTest(properties = "spring.autoconfigure.exclude="
        + "org.springframework.boot.autoconfigure.mongo.MongoAutoConfiguration,"
        + "org.springframework.boot.autoconfigure.data.mongo.MongoDataAutoConfiguration,"
        + "org.springframework.boot.autoconfigure.data.mongo.MongoRepositoriesAutoConfiguration")
@AutoConfigureMockMvc
class OpenApiExportTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UserRepository userRepository;

    @MockBean
    private UserAuditEventRepository auditEventRepository;

    @MockBean
    private UserIndexInitializer indexInitializer;

    @Test
    @DisplayName("Exporta openapi.json y openapi.yaml del grupo v1 a delivery/openapi")
    void exportaLaEspecificacionOpenApi() throws Exception {
        String json = mockMvc.perform(get("/v3/api-docs/v1"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        String yaml = mockMvc.perform(get("/v3/api-docs.yaml/v1"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);

        Path dir = Path.of("delivery", "openapi");
        Files.createDirectories(dir);
        ObjectMapper mapper = new ObjectMapper();
        JsonNode tree = mapper.readTree(json);
        Files.writeString(dir.resolve("openapi.json"), mapper.writerWithDefaultPrettyPrinter().writeValueAsString(tree));
        Files.writeString(dir.resolve("openapi.yaml"), yaml);

        JsonNode paths = tree.path("paths");
        assertThat(paths.size()).isGreaterThan(0);
        assertThat(paths.has("/api/v1/users")).isTrue();
        assertThat(paths.path("/api/v1/users").has("post")).isTrue();
        assertThat(paths.path("/api/v1/users/{id}").has("get")).isTrue();
        assertThat(paths.path("/api/v1/users/{id}").has("patch")).isTrue();
        assertThat(paths.path("/api/v1/users/{id}/status").has("patch")).isTrue();
        assertThat(paths.path("/api/v1/users/{id}/audit").has("get")).isTrue();
    }
}
