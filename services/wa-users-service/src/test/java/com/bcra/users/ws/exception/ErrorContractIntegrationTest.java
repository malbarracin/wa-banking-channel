package com.bcra.users.ws.exception;

import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(GlobalExceptionHandlerTest.ErrorProbeController.class)
class ErrorContractIntegrationTest {

    @ParameterizedTest
    @CsvSource({
        "/probe/illegal,400,BAD_REQUEST",
        "/probe/number?value=texto,400,BAD_REQUEST",
        "/probe/number,400,BAD_REQUEST",
        "/probe/missing,404,NOT_FOUND",
        "/probe/conflict,409,CONFLICT",
        "/probe/unexpected,500,INTERNAL_ERROR"
    })
    @DisplayName("Spring registra el advice y serializa el contrato completo para cada error HTTP")
    void respondeErroresConElAdviceRegistrado(String path, int httpStatus, String code,
            @Autowired MockMvc mockMvc, @Autowired ObjectMapper objectMapper) throws Exception {
        Instant inicio = Instant.now();

        var response = mockMvc.perform(get(path))
                .andExpect(status().is(httpStatus))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value(code))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.details").isEmpty())
                .andReturn().getResponse();

        var body = objectMapper.readTree(response.getContentAsString());
        assertThat(body.properties()).extracting(entry -> entry.getKey())
                .containsExactlyInAnyOrder("code", "message", "details", "timestamp");
        assertThat(Instant.parse(body.get("timestamp").asText())).isBetween(inicio, Instant.now());
        assertThat(body.get("timestamp").asText()).endsWith("Z");
        assertThat(response.getContentAsString()).doesNotContain("sensitive", "java.lang", "stackTrace");
    }

    @Test
    @DisplayName("Una solicitud válida atraviesa la validación sin activar el advice")
    void aceptaBodyValido(@Autowired MockMvc mockMvc) throws Exception {
        mockMvc.perform(post("/probe/body")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Ana\"}"))
                .andExpect(status().isOk())
                .andExpect(content().string(""));
    }

    @Test
    @DisplayName("Spring devuelve los detalles de validación del body con el contrato único")
    void detallaBodyInvalido(@Autowired MockMvc mockMvc) throws Exception {
        mockMvc.perform(post("/probe/body")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.details[0].field").value("name"))
                .andExpect(jsonPath("$.details[0].message").isNotEmpty())
                .andExpect(jsonPath("$.timestamp").isString());
    }
}
