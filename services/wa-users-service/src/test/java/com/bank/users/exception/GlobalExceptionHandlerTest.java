package com.bank.users.exception;

import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("GlobalExceptionHandler — contrato uniforme de errores")
class GlobalExceptionHandlerTest {

    private static final String SECRET = "password=s3cr3t";

    private final ObjectMapper mapper = Jackson2ObjectMapperBuilder.json()
            .failOnUnknownProperties(true)
            .build();

    private MockMvc mvc;

    enum Kind { ALPHA, BETA }

    record Payload(@NotBlank String name, @NotNull Kind kind) {
    }

    @RestController
    static class FakeController {

        @PostMapping("/payload")
        Payload create(@Valid @RequestBody Payload payload) {
            return payload;
        }

        @GetMapping("/kind")
        String kind(@RequestParam Kind kind) {
            return kind.name();
        }

        @GetMapping("/items/{id}")
        String item(@PathVariable Long id) {
            switch (id.intValue()) {
                case 0 -> {
                    return "ok";
                }
                case 1 -> throw UserNotFoundException.byId(id);
                case 2 -> throw UserNotFoundException.byDocument();
                case 3 -> throw new UserAlreadyExistsException();
                case 4 -> throw InvalidStateTransitionException.terminalState();
                case 5 -> throw InvalidStateTransitionException.notAllowed(Kind.ALPHA, Kind.BETA);
                case 6 -> throw new NonModifiableFieldException(List.of("documentNumber", "status"));
                case 7 -> throw new DataIntegrityViolationException("uk_document " + SECRET);
                case 8 -> throw new ConstraintViolationException("invalid", java.util.Set.of());
                default -> throw new IllegalStateException("db down " + SECRET);
            }
        }
    }

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new FakeController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(mapper))
                .build();
    }

    @Test
    @DisplayName("ErrorResponse serializa solo code, message, details (array) y timestamp ISO-8601")
    void serializesOnlyContractFields() throws Exception {
        JsonNode json = mapper.readTree(mapper.writeValueAsString(
                ErrorResponse.of(ErrorCode.NOT_FOUND, "x")));

        assertThat(json.fieldNames()).toIterable()
                .containsExactlyInAnyOrder("code", "message", "details", "timestamp");
        assertThat(json.get("details").isArray()).isTrue();
        assertThat(json.get("details")).isEmpty();
        assertThat(Instant.parse(json.get("timestamp").asText())).isNotNull();
        assertThat(new ErrorResponse(ErrorCode.CONFLICT, "y", null, "t").details()).isEmpty();
    }

    @Test
    @DisplayName("Campos faltantes/inválidos -> 400 VALIDATION_ERROR identificando cada campo")
    void validationError() throws Exception {
        JsonNode body = body(perform(post("/payload").content("{\"name\":\"\"}")), 400);

        assertThat(body.get("code").asText()).isEqualTo("VALIDATION_ERROR");
        assertThat(body.get("details").findValuesAsText("field")).containsExactlyInAnyOrder("name", "kind");
    }

    @Test
    @DisplayName("Parámetro obligatorio ausente -> 400 VALIDATION_ERROR")
    void missingParameter() throws Exception {
        JsonNode body = body(perform(get("/kind")), 400);

        assertThat(body.get("code").asText()).isEqualTo("VALIDATION_ERROR");
        assertThat(body.get("details").get(0).get("field").asText()).isEqualTo("kind");
    }

    @Test
    @DisplayName("JSON inválido -> 400 BAD_REQUEST")
    void malformedJson() throws Exception {
        JsonNode body = body(perform(post("/payload").content("{not json")), 400);

        assertThat(body.get("code").asText()).isEqualTo("BAD_REQUEST");
        assertThat(body.get("message").asText()).isEqualTo(GlobalExceptionHandler.MALFORMED_JSON_MESSAGE);
    }

    @Test
    @DisplayName("Tipo de dato incorrecto en el cuerpo -> 400 BAD_REQUEST con el campo")
    void wrongTypeInBody() throws Exception {
        JsonNode body = body(perform(post("/payload").content("{\"name\":{\"a\":1},\"kind\":\"ALPHA\"}")), 400);

        assertThat(body.get("code").asText()).isEqualTo("BAD_REQUEST");
        assertThat(body.get("details").get(0).get("field").asText()).isEqualTo("name");
    }

    @Test
    @DisplayName("Enum desconocido en el cuerpo -> 400 BAD_REQUEST sin eco del valor")
    void unknownEnumInBody() throws Exception {
        String raw = body(perform(post("/payload").content("{\"name\":\"n\",\"kind\":\"GAMMA\"}")), 400).toString();
        JsonNode body = mapper.readTree(raw);

        assertThat(body.get("code").asText()).isEqualTo("BAD_REQUEST");
        assertThat(body.get("details").get(0).get("field").asText()).isEqualTo("kind");
        assertThat(body.get("details").get(0).get("message").asText()).contains("ALPHA, BETA");
        assertThat(raw).doesNotContain("GAMMA");
    }

    @Test
    @DisplayName("Enum desconocido en query param -> 400 BAD_REQUEST")
    void unknownEnumInParam() throws Exception {
        JsonNode body = body(perform(get("/kind").param("kind", "GAMMA")), 400);

        assertThat(body.get("code").asText()).isEqualTo("BAD_REQUEST");
        assertThat(body.get("details").get(0).get("field").asText()).isEqualTo("kind");
    }

    @Test
    @DisplayName("Tipo inválido en path variable -> 400 BAD_REQUEST")
    void wrongPathVariableType() throws Exception {
        JsonNode body = body(perform(get("/items/abc")), 400);

        assertThat(body.get("code").asText()).isEqualTo("BAD_REQUEST");
        assertThat(body.get("details").get(0).get("message").asText())
                .isEqualTo(GlobalExceptionHandler.INVALID_FORMAT_DETAIL);
    }

    @Test
    @DisplayName("Campo prohibido en el cuerpo -> 400 BAD_REQUEST «campo no modificable por este servicio»")
    void unknownFieldInBody() throws Exception {
        JsonNode body = body(perform(post("/payload")
                .content("{\"name\":\"n\",\"kind\":\"ALPHA\",\"status\":\"INACTIVE\"}")), 400);

        assertThat(body.get("code").asText()).isEqualTo("BAD_REQUEST");
        assertThat(body.get("message").asText()).isEqualTo("campo no modificable por este servicio");
        assertThat(body.get("details").get(0).get("field").asText()).isEqualTo("status");
    }

    @Test
    @DisplayName("NonModifiableFieldException -> 400 BAD_REQUEST con un detalle por campo")
    void nonModifiableFieldException() throws Exception {
        JsonNode body = body(perform(get("/items/6")), 400);

        assertThat(body.get("code").asText()).isEqualTo("BAD_REQUEST");
        assertThat(body.get("details").findValuesAsText("field")).containsExactly("documentNumber", "status");
        assertThat(body.get("details").findValuesAsText("message"))
                .containsOnly("campo no modificable por este servicio");
        assertThat(new NonModifiableFieldException(null).getFields()).isEmpty();
    }

    @Test
    @DisplayName("Usuario ausente -> 404 NOT_FOUND (por id y por documento)")
    void notFound() throws Exception {
        assertThat(body(perform(get("/items/1")), 404).get("code").asText()).isEqualTo("NOT_FOUND");
        JsonNode byDocument = body(perform(get("/items/2")), 404);
        assertThat(byDocument.get("code").asText()).isEqualTo("NOT_FOUND");
    }

    @Test
    @DisplayName("Documento duplicado -> 409 CONFLICT")
    void duplicate() throws Exception {
        JsonNode body = body(perform(get("/items/3")), 409);

        assertThat(body.get("code").asText()).isEqualTo("CONFLICT");
        assertThat(body.get("message").asText()).isEqualTo(UserAlreadyExistsException.DEFAULT_MESSAGE);
    }

    @Test
    @DisplayName("Violación de integridad de BD -> 409 CONFLICT sin detalles internos")
    void dataIntegrity() throws Exception {
        String raw = body(perform(get("/items/7")), 409).toString();

        assertThat(mapper.readTree(raw).get("code").asText()).isEqualTo("CONFLICT");
        assertThat(raw).doesNotContain(SECRET).doesNotContain("uk_document");
    }

    @Test
    @DisplayName("Transición desde INACTIVE -> 400 BAD_REQUEST «la baja lógica es un estado terminal»")
    void terminalState() throws Exception {
        JsonNode body = body(perform(get("/items/4")), 400);

        assertThat(body.get("code").asText()).isEqualTo("BAD_REQUEST");
        assertThat(body.get("message").asText()).isEqualTo("la baja lógica es un estado terminal");
    }

    @Test
    @DisplayName("Otra transición inválida -> 400 BAD_REQUEST")
    void transitionNotAllowed() throws Exception {
        JsonNode body = body(perform(get("/items/5")), 400);

        assertThat(body.get("code").asText()).isEqualTo("BAD_REQUEST");
        assertThat(body.get("message").asText()).contains("ALPHA").contains("BETA");
    }

    @Test
    @DisplayName("ConstraintViolationException -> 400 VALIDATION_ERROR")
    void constraintViolation() throws Exception {
        assertThat(body(perform(get("/items/8")), 400).get("code").asText()).isEqualTo("VALIDATION_ERROR");
    }

    @Test
    @DisplayName("Fallo inesperado -> 500 INTERNAL_ERROR sin stacktrace ni datos sensibles")
    void unexpected() throws Exception {
        String raw = body(perform(get("/items/99")), 500).toString();
        JsonNode body = mapper.readTree(raw);

        assertThat(body.get("code").asText()).isEqualTo("INTERNAL_ERROR");
        assertThat(body.get("message").asText()).isEqualTo(GlobalExceptionHandler.INTERNAL_MESSAGE);
        assertThat(raw).doesNotContain(SECRET).doesNotContain("IllegalStateException").doesNotContain("at com.");
    }

    @Test
    @DisplayName("Método no soportado -> mantiene 405 con contrato uniforme BAD_REQUEST")
    void methodNotAllowed() throws Exception {
        JsonNode body = body(perform(put("/kind")), 405);

        assertThat(body.get("code").asText()).isEqualTo("BAD_REQUEST");
        assertThat(body.fieldNames()).toIterable()
                .containsExactlyInAnyOrder("code", "message", "details", "timestamp");
    }

    @Test
    @DisplayName("Ruta inexistente y errores 5xx de Spring MVC usan el contrato uniforme")
    void springInternalStatuses() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        var headers = new org.springframework.http.HttpHeaders();

        var notFound = handler.handleExceptionInternal(new RuntimeException(), null, headers,
                org.springframework.http.HttpStatus.NOT_FOUND, null);
        var internal = handler.handleExceptionInternal(new RuntimeException(), null, headers,
                org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE, null);

        assertThat(((ErrorResponse) notFound.getBody()).code()).isEqualTo(ErrorCode.NOT_FOUND);
        assertThat(((ErrorResponse) internal.getBody()).code()).isEqualTo(ErrorCode.INTERNAL_ERROR);
        assertThat(internal.getStatusCode().value()).isEqualTo(503);
    }

    private ResultActions perform(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder req)
            throws Exception {
        return mvc.perform(req.contentType(MediaType.APPLICATION_JSON));
    }

    private JsonNode body(ResultActions actions, int expectedStatus) throws Exception {
        MvcResult result = actions.andExpect(status().is(expectedStatus)).andReturn();
        String raw = result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        JsonNode json = mapper.readTree(raw);
        assertThat(json.fieldNames()).toIterable()
                .containsExactlyInAnyOrder("code", "message", "details", "timestamp");
        return json;
    }
}
