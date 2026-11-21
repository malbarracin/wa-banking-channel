package com.bcra.users.ws;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Path;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
class OpenApiContractTest {
    private static final Set<String> METHODS = Set.of("get", "post", "put", "patch", "delete", "options", "head", "trace");

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper mapper;

    @Test
    void publishedPathsAndMethodsMatchSpringdoc() throws Exception {
        JsonNode published = new ObjectMapper(new YAMLFactory()).readTree(
                Path.of("../../shared/contracts/wa-users-service/openapi.yaml").toFile());
        String json = mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(published.path("openapi").asText()).startsWith("3.");
        assertThat(operations(published.path("paths"))).isEqualTo(operations(mapper.readTree(json).path("paths")));
        assertThat(operations(published.path("paths")).values().stream().mapToInt(Set::size).sum()).isEqualTo(6);
    }

    private Map<String, Set<String>> operations(JsonNode paths) {
        Map<String, Set<String>> result = new TreeMap<>();
        paths.fields().forEachRemaining(path -> {
            Set<String> methods = new TreeSet<>();
            Iterator<String> names = path.getValue().fieldNames();
            names.forEachRemaining(name -> { if (METHODS.contains(name)) { methods.add(name); } });
            result.put(path.getKey(), methods);
        });
        return result;
    }
}
