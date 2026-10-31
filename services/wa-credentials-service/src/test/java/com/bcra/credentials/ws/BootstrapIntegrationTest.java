package com.bcra.credentials.ws;

import java.time.Instant;

import javax.sql.DataSource;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BootstrapIntegrationTest {

    @Test
    @DisplayName("El endpoint health confirma que la aplicación y su base están disponibles")
    void exponeHealth(@Autowired MockMvc mockMvc) throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    @DisplayName("El endpoint info está expuesto")
    void exponeInfo(@Autowired MockMvc mockMvc) throws Exception {
        mockMvc.perform(get("/actuator/info"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Una ruta inexistente devuelve el contrato seguro con timestamp UTC")
    void respondeNotFoundEnElContextoCompleto(@Autowired MockMvc mockMvc,
            @Autowired ObjectMapper objectMapper) throws Exception {
        var response = mockMvc.perform(get("/api/v1/recurso-inexistente"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("El recurso no existe"))
                .andExpect(jsonPath("$.details", hasSize(0)))
                .andExpect(jsonPath("$.trace").doesNotExist())
                .andExpect(jsonPath("$.exception").doesNotExist())
                .andReturn().getResponse();

        var timestamp = objectMapper.readTree(response.getContentAsString()).get("timestamp").asText();
        assertThat(timestamp).endsWith("Z");
        assertThat(Instant.parse(timestamp)).isBeforeOrEqualTo(Instant.now());
    }

    @Test
    @DisplayName("El perfil test abre una conexión H2 sin requerir PostgreSQL")
    void conectaConH2(@Autowired DataSource dataSource) throws Exception {
        try (var connection = dataSource.getConnection()) {
            assertThat(connection.getMetaData().getDatabaseProductName()).isEqualTo("H2");
            assertThat(connection.isValid(1)).isTrue();
        }
    }
}
