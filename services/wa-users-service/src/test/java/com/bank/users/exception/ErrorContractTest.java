package com.bank.users.exception;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.ConstraintViolationException;
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
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("TASK-2: contrato HTTP y campos de validación")
class ErrorContractTest {

    private final ObjectMapper mapper = Jackson2ObjectMapperBuilder.json()
            .failOnUnknownProperties(true).build();
    private MockMvc mvc;

    record Contact(@NotBlank String email) {
    }

    record Envelope(@Valid Contact contact, List<GlobalExceptionHandlerTest.Kind> kinds) {
    }

    @RestController
    static class ValidationController {

        @PostMapping("/nested")
        Envelope nested(@Valid @RequestBody Envelope payload) {
            return payload;
        }

        @GetMapping("/minimum")
        int minimum(@RequestParam @Min(1) int count) {
            return count;
        }

        @GetMapping("/constraint")
        void constraint() {
            try (var factory = Validation.buildDefaultValidatorFactory()) {
                var violations = factory.getValidator().validate(
                        new Envelope(new Contact(""), List.of()));
                throw new ConstraintViolationException(violations);
            }
        }
    }

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new ValidationController(),
                        new GlobalExceptionHandlerTest.FakeController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(mapper))
                .build();
    }

    @Test
    @DisplayName("Solicitud válida atraviesa el advice y conserva la respuesta exitosa")
    void validRequest() throws Exception {
        var response = mvc.perform(post("/payload").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"cliente\",\"kind\":\"ALPHA\"}"))
                .andExpect(status().isOk()).andReturn().getResponse();

        assertThat(mapper.readTree(response.getContentAsString()).get("name").asText())
                .isEqualTo("cliente");
    }

    @ParameterizedTest
    @CsvSource({"1,404,NOT_FOUND", "3,409,CONFLICT", "4,400,BAD_REQUEST", "99,500,INTERNAL_ERROR"})
    @DisplayName("Cada error de dominio publica solo el contrato con array vacío y timestamp actual")
    void domainContract(int id, int expectedStatus, String code) throws Exception {
        Instant before = Instant.now();
        JsonNode body = response(get("/items/" + id), expectedStatus);

        assertThat(body.get("code").asText()).isEqualTo(code);
        assertThat(body.get("message").asText()).isNotBlank();
        assertThat(body.get("details").isArray()).isTrue();
        assertThat(body.get("details")).isEmpty();
        assertThat(Instant.parse(body.get("timestamp").asText())).isBetween(before, Instant.now());
    }

    @Test
    @DisplayName("Violación real de Bean Validation identifica el campo anidado sin exponer valores")
    void realConstraintViolation() throws Exception {
        JsonNode body = response(get("/constraint"), 400);

        assertThat(body.get("code").asText()).isEqualTo("VALIDATION_ERROR");
        assertThat(body.get("details").get(0).get("field").asText()).isEqualTo("email");
        assertThat(body.get("details").get(0).get("message").asText()).isNotBlank();
    }

    @Test
    @DisplayName("Validación de parámetro de método identifica count")
    void methodParameterValidation() throws Exception {
        JsonNode body = response(get("/minimum").param("count", "0"), 400);

        assertThat(body.get("code").asText()).isEqualTo("VALIDATION_ERROR");
        assertThat(body.get("details").get(0).get("field").asText()).isEqualTo("count");
    }

    @Test
    @DisplayName("Enum inválido dentro de array identifica ruta e índice sin eco del valor")
    void nestedArrayEnum() throws Exception {
        JsonNode body = response(post("/nested")
                .content("{\"contact\":{\"email\":\"a@b.test\"},\"kinds\":[\"secret-value\"]}"), 400);

        assertThat(body.get("code").asText()).isEqualTo("BAD_REQUEST");
        assertThat(body.get("details").get(0).get("field").asText()).isEqualTo("kinds[0]");
        assertThat(body.toString()).doesNotContain("secret-value", "a@b.test");
    }

    @Test
    @DisplayName("Campo prohibido anidado identifica la ruta y omite su valor sensible")
    void nestedForbiddenField() throws Exception {
        JsonNode body = response(post("/nested")
                .content("{\"contact\":{\"email\":\"a@b.test\",\"password\":\"secret-value\"}}"), 400);

        assertThat(body.get("code").asText()).isEqualTo("BAD_REQUEST");
        assertThat(body.get("message").asText()).isEqualTo("campo no modificable por este servicio");
        assertThat(body.get("details").get(0).get("field").asText()).isEqualTo("contact.password");
        assertThat(body.toString()).doesNotContain("secret-value", "a@b.test");
    }

    private JsonNode response(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request,
            int expectedStatus) throws Exception {
        var result = mvc.perform(request.contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().is(expectedStatus)).andReturn().getResponse();
        assertThat(result.getContentType()).startsWith(MediaType.APPLICATION_JSON_VALUE);
        JsonNode body = mapper.readTree(result.getContentAsString(StandardCharsets.UTF_8));
        assertThat(body.fieldNames()).toIterable()
                .containsExactlyInAnyOrder("code", "message", "details", "timestamp");
        assertThat(body.get("details").isArray()).isTrue();
        assertThat(Instant.parse(body.get("timestamp").asText())).isNotNull();
        return body;
    }
}
