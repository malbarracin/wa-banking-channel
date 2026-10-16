package com.bank.users.exception;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.Validation;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GlobalExceptionHandlerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private MockMvc mockMvc;

    @BeforeEach
    void configurarMvc() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ContractController())
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @Test
    @DisplayName("Una solicitud válida devuelve el resultado sin envoltorio de error")
    void aceptaSolicitudValida() throws Exception {
        mockMvc.perform(post("/contract").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"válido\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.name").value("válido"));
    }

    @ParameterizedTest
    @CsvSource({"missing,404,NOT_FOUND", "conflict,409,CONFLICT", "unexpected,500,INTERNAL_ERROR",
            "illegal,400,BAD_REQUEST", "constraint,400,VALIDATION_ERROR"})
    @DisplayName("Las excepciones usan el status y contrato esperado sin filtrar datos")
    void traduceExcepciones(String scenario, int expectedStatus, String expectedCode) throws Exception {
        verificarError(get("/contract/errors/" + scenario), expectedStatus, expectedCode);
    }

    @Test
    @DisplayName("Bean Validation devuelve los campos inválidos sin el valor rechazado")
    void rechazaCamposInvalidos() throws Exception {
        verificarError(post("/contract").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"\"}"),
                400, "VALIDATION_ERROR");
        mockMvc.perform(post("/contract").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(jsonPath("$.details[0].field").value("name"))
                .andExpect(jsonPath("$.details[0].message").isNotEmpty());
    }

    @Test
    @DisplayName("UUID, JSON y parámetros inválidos conservan BAD_REQUEST")
    void rechazaSolicitudesMalformadas() throws Exception {
        verificarError(get("/contract/uuid/no-es-uuid"), 400, "BAD_REQUEST");
        verificarError(post("/contract").contentType(MediaType.APPLICATION_JSON).content("{"),
                400, "BAD_REQUEST");
        verificarError(get("/contract/required"), 400, "BAD_REQUEST");
    }

    @Test
    @DisplayName("La validación de parámetros MVC conserva VALIDATION_ERROR")
    void rechazaParametroFueraDeRango() throws Exception {
        verificarError(get("/contract/minimum").param("number", "0"), 400, "VALIDATION_ERROR");
    }

    @Test
    @DisplayName("La respuesta copia los detalles y no permite modificarlos")
    void conservaDetallesInmutables() {
        List<FieldError> details = new ArrayList<>();
        details.add(new FieldError("name", "inválido"));
        var response = new ErrorResponse(ErrorCode.VALIDATION_ERROR, "inválido", details, Instant.now());
        details.clear();
        assertThat(response.details()).hasSize(1);
        assertThatThrownBy(() -> response.details().clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    private void verificarError(MockHttpServletRequestBuilder request, int expectedStatus, String code)
            throws Exception {
        MvcResult result = mockMvc.perform(request).andExpect(status().is(expectedStatus))
                .andExpect(jsonPath("$.code").value(code))
                .andExpect(jsonPath("$.details").isArray())
                .andExpect(jsonPath("$.timestamp").isNotEmpty()).andReturn();
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsByteArray());
        assertThat(body.size()).isEqualTo(4);
        assertThat(Instant.parse(body.get("timestamp").asText())).isBeforeOrEqualTo(Instant.now());
        assertThat(body.toString()).doesNotContain("PII_SECRETA", "stackTrace", "RuntimeException", "SELECT");
    }

    record ValidRequest(@NotBlank String name) {
    }

    @RestController
    static class ContractController {

        @PostMapping("/contract")
        ValidRequest validar(@Valid @RequestBody ValidRequest request) {
            return request;
        }

        @GetMapping("/contract/uuid/{id}")
        UUID uuid(@PathVariable UUID id) {
            return id;
        }

        @GetMapping("/contract/required")
        String parametro(@RequestParam String name) {
            return name;
        }

        @GetMapping("/contract/minimum")
        int minimo(@RequestParam @Min(1) int number) {
            return number;
        }

        @GetMapping("/contract/errors/{scenario}")
        String error(@PathVariable String scenario) {
            switch (scenario) {
                case "missing" -> throw new NotFoundException("PII_SECRETA") { };
                case "conflict" -> throw new ConflictException("PII_SECRETA") { };
                case "illegal" -> throw new IllegalArgumentException("PII_SECRETA");
                case "constraint" -> {
                    try (var factory = Validation.buildDefaultValidatorFactory()) {
                        var violations = factory.getValidator().validate(new ValidRequest(""));
                        throw new jakarta.validation.ConstraintViolationException("PII_SECRETA", violations);
                    }
                }
                default -> throw new IllegalStateException("SELECT PII_SECRETA");
            }
        }
    }
}
