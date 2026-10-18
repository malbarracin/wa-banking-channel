package com.bank.users.exception;

import java.time.Instant;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(GlobalExceptionHandlerTest.ErrorProbeController.class)
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class ErrorContractIntegrationTest {

    private final MockMvc mockMvc;
    private final ObjectMapper objectMapper;

    ErrorContractIntegrationTest(MockMvc mockMvc, ObjectMapper objectMapper) {
        this.mockMvc = mockMvc;
        this.objectMapper = objectMapper;
    }

    @ParameterizedTest
    @CsvSource({
        "/probe/missing,404,NOT_FOUND,Recurso inexistente",
        "/probe/conflict,409,CONFLICT,Conflicto de dominio",
        "/probe/unexpected,500,INTERNAL_ERROR,Error interno",
        "/probe/argument,400,BAD_REQUEST,Solicitud inválida",
        "/probe/parameter,400,BAD_REQUEST,Solicitud inválida",
        "/probe/parameter?id=invalid,400,BAD_REQUEST,Solicitud inválida"
    })
    @DisplayName("El advice real conserva mensajes públicos y detalles vacíos con fecha UTC reciente")
    void returnsPublicErrorContract(String path, int expectedStatus, String expectedCode, String expectedMessage)
            throws Exception {
        Instant startedAt = Instant.now();
        String content = mockMvc.perform(get(path))
                .andExpect(status().is(expectedStatus))
                .andExpect(jsonPath("$.code").value(expectedCode))
                .andExpect(jsonPath("$.message").value(expectedMessage))
                .andExpect(jsonPath("$.details").isEmpty())
                .andReturn().getResponse().getContentAsString();

        JsonNode body = objectMapper.readTree(content);
        assertThat(body.size()).isEqualTo(4);
        String timestamp = body.get("timestamp").asText();
        assertThat(timestamp).endsWith("Z");
        assertThat(Instant.parse(timestamp)).isBetween(startedAt, Instant.now());
        assertThat(content).doesNotContain("secret-value", "SELECT", "IllegalStateException", "stackTrace");
    }

    @Test
    @DisplayName("Las restricciones incluyen campo y mensaje a través del advice registrado por Spring")
    void returnsConstraintFieldDetails() throws Exception {
        mockMvc.perform(get("/probe/constraint"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value("La solicitud tiene datos inválidos"))
                .andExpect(jsonPath("$.details.length()").value(1))
                .andExpect(jsonPath("$.details[0].field").value("name"))
                .andExpect(jsonPath("$.details[0].message").isNotEmpty())
                .andExpect(jsonPath("$.timestamp").isNotEmpty());
    }
}
