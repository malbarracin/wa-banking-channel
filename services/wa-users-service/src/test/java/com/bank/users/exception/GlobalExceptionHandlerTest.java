package com.bank.users.exception;

import java.time.Instant;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.Validation;
import jakarta.validation.constraints.NotBlank;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GlobalExceptionHandlerTest {

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ErrorProbeController())
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @Test
    @DisplayName("Un body inválido devuelve detalles de validación sin el valor rechazado")
    void rejectsInvalidBody() throws Exception {
        assertContract(mockMvc.perform(post("/probe").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"\"}")), 400, "VALIDATION_ERROR")
                .andExpect(jsonPath("$.details[0].field").value("name"))
                .andExpect(jsonPath("$.details[0].message").isNotEmpty());
    }

    @ParameterizedTest
    @CsvSource({"/probe/missing,404,NOT_FOUND", "/probe/conflict,409,CONFLICT",
            "/probe/unexpected,500,INTERNAL_ERROR", "/probe/argument,400,BAD_REQUEST",
            "/probe/parameter,400,BAD_REQUEST", "/probe/parameter?id=invalid,400,BAD_REQUEST",
            "/probe/constraint,400,VALIDATION_ERROR"})
    @DisplayName("Cada excepción devuelve su HTTP y el contrato común sin datos internos")
    void mapsExceptions(String path, int expectedStatus, String expectedCode) throws Exception {
        assertContract(mockMvc.perform(get(path)), expectedStatus, expectedCode);
    }

    @ParameterizedTest
    @CsvSource({"'{'", "'{\"name\":[]}'"})
    @DisplayName("JSON mal formado o incompatible devuelve BAD_REQUEST")
    void rejectsMalformedJson(String content) throws Exception {
        assertContract(mockMvc.perform(post("/probe").contentType(MediaType.APPLICATION_JSON)
                .content(content)), 400, "BAD_REQUEST");
    }

    @Test
    @DisplayName("Un body válido conserva la respuesta correcta")
    void acceptsValidBody() throws Exception {
        mockMvc.perform(post("/probe").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Nombre\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Nombre"));
    }

    private ResultActions assertContract(ResultActions result, int expectedStatus, String expectedCode)
            throws Exception {
        result.andExpect(status().is(expectedStatus))
                .andExpect(jsonPath("$.code").value(expectedCode))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.details").isArray())
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.trace").doesNotExist());
        String content = result.andReturn().getResponse().getContentAsString();
        JsonNode body = objectMapper.readTree(content);
        assertThat(body.size()).isEqualTo(4);
        assertThat(Instant.parse(body.get("timestamp").asText())).isBeforeOrEqualTo(Instant.now());
        assertThat(content).doesNotContain("secret-value", "IllegalStateException", "java.lang", "SELECT");
        return result;
    }

    record ProbeRequest(@NotBlank String name) {
    }

    @RestController
    static class ErrorProbeController {

        @PostMapping("/probe")
        public ProbeRequest validate(@Valid @RequestBody ProbeRequest request) {
            return request;
        }

        @GetMapping("/probe/parameter")
        public UUID parameter(@RequestParam UUID id) {
            return id;
        }

        @GetMapping("/probe/missing")
        public void missing() {
            throw new NotFoundException("Recurso inexistente") { };
        }

        @GetMapping("/probe/conflict")
        public void conflict() {
            throw new ConflictException("Conflicto de dominio") { };
        }

        @GetMapping("/probe/argument")
        public void argument() {
            throw new IllegalArgumentException("secret-value");
        }

        @GetMapping("/probe/unexpected")
        public void unexpected() {
            throw new IllegalStateException("SELECT secret-value");
        }

        @GetMapping("/probe/constraint")
        public void constraint() {
            try (var factory = Validation.buildDefaultValidatorFactory()) {
                throw new jakarta.validation.ConstraintViolationException(
                        factory.getValidator().validate(new ProbeRequest("")));
            }
        }
    }
}
