package com.bcra.accounts.ws.exception;

import java.time.Instant;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.Validation;
import jakarta.validation.constraints.NotBlank;

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
import org.springframework.web.servlet.resource.NoResourceFoundException;

import com.fasterxml.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GlobalExceptionHandlerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void configuraHttp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ErrorProbeController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @ParameterizedTest
    @CsvSource({
        "/probe/illegal,400,BAD_REQUEST",
        "/probe/number?value=texto,400,BAD_REQUEST",
        "/probe/number,400,BAD_REQUEST",
        "/probe/missing,404,NOT_FOUND",
        "/probe/conflict,409,CONFLICT",
        "/probe/unauthorized,401,UNAUTHORIZED",
        "/probe/forbidden,403,FORBIDDEN",
        "/probe/unexpected,500,INTERNAL_ERROR"
    })
    @DisplayName("Los errores HTTP respetan el contrato y no filtran detalles internos")
    void respondeElContrato(String path, int httpStatus, String code) throws Exception {
        ResultActions response = mockMvc.perform(get(path));

        response.andExpect(status().is(httpStatus))
                .andExpect(jsonPath("$.code").value(code))
                .andExpect(jsonPath("$.details", hasSize(0)))
                .andExpect(jsonPath("$.timestamp").isString())
                .andExpect(jsonPath("$.trace").doesNotExist())
                .andExpect(jsonPath("$.exception").doesNotExist());
        String body = response.andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain("sensitive");
        var error = new ObjectMapper().readTree(body);
        assertThat(error.size()).isEqualTo(4);
        assertThat(error.get("message").asText()).isNotBlank();
        assertThat(Instant.parse(error.get("timestamp").asText())).isBeforeOrEqualTo(Instant.now());
        assertThat(error.get("timestamp").asText()).endsWith("Z");
    }

    @Test
    @DisplayName("Un recurso HTTP inexistente devuelve 404 sin revelar su ruta")
    void ocultaLaRutaDelRecursoInexistente() throws Exception {
        mockMvc.perform(get("/probe/resource"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("El recurso no existe"))
                .andExpect(jsonPath("$.details", hasSize(0)))
                .andExpect(jsonPath("$.timestamp").isString())
                .andExpect(jsonPath("$.trace").doesNotExist());
    }

    @Test
    @DisplayName("Un body inválido devuelve los campos rechazados sin sus valores")
    void detallaLaValidacionDelBody() throws Exception {
        mockMvc.perform(post("/probe/body").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.details[0].field").value("name"))
                .andExpect(jsonPath("$.details[0].message").isString())
                .andExpect(jsonPath("$.timestamp").isString());
    }

    @Test
    @DisplayName("Un JSON mal formado devuelve BAD_REQUEST sin el mensaje del parser")
    void rechazaJsonMalFormado() throws Exception {
        mockMvc.perform(post("/probe/body").contentType(MediaType.APPLICATION_JSON).content("{"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.details", hasSize(0)))
                .andExpect(jsonPath("$.timestamp").isString());
    }

    @Test
    @DisplayName("Las violaciones de constraints usan el contrato de validación")
    void detallaConstraints() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var violations = factory.getValidator().validate(new ProbeRequest(""));
            var exception = new jakarta.validation.ConstraintViolationException(violations);

            var response = new GlobalExceptionHandler().handleConstraintViolation(exception);

            assertThat(response.getStatusCode().value()).isEqualTo(400);
            assertThat(response.getBody().code()).isEqualTo(ErrorCode.VALIDATION_ERROR);
            assertThat(response.getBody().details()).extracting(FieldError::field).containsExactly("name");
            assertThat(response.getBody().timestamp()).isBeforeOrEqualTo(Instant.now());
        }
    }

    @Test
    @DisplayName("Las violaciones de constraints llegan al cliente como 400 con detalles sin valores rechazados")
    void respondeConstraintsPorHttp() throws Exception {
        mockMvc.perform(get("/probe/constraint"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value("La solicitud tiene datos inválidos"))
                .andExpect(jsonPath("$.details", hasSize(1)))
                .andExpect(jsonPath("$.details[0].field").value("name"))
                .andExpect(jsonPath("$.details[0].message").isNotEmpty())
                .andExpect(jsonPath("$.details[0].rejectedValue").doesNotExist())
                .andExpect(jsonPath("$.timestamp").isString())
                .andExpect(jsonPath("$.trace").doesNotExist())
                .andExpect(jsonPath("$.exception").doesNotExist());
    }

    @Test
    @DisplayName("La respuesta expone una lista de detalles inmutable")
    void protegeLaListaDeDetalles() {
        var response = new ErrorResponse(ErrorCode.BAD_REQUEST, "Inválido", List.of(), Instant.now());

        assertThatThrownBy(() -> response.details().add(new FieldError("name", "Inválido")))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    record ProbeRequest(@NotBlank String name) {
    }

    @RestController
    static class ErrorProbeController {

        @GetMapping("/probe/unauthorized")
        public void unauthorized() {
            throw new UnauthorizedException("sensitive");
        }

        @GetMapping("/probe/forbidden")
        public void forbidden() {
            throw new ForbiddenException("sensitive");
        }

        @GetMapping("/probe/illegal")
        public void illegal() {
            throw new IllegalArgumentException("sensitive");
        }

        @GetMapping("/probe/number")
        public int number(@RequestParam int value) {
            return value;
        }

        @GetMapping("/probe/missing")
        public void missing() {
            throw new NotFoundException("No existe el recurso") { };
        }

        @GetMapping("/probe/conflict")
        public void conflict() {
            throw new ConflictException("El recurso ya existe") { };
        }

        @GetMapping("/probe/unexpected")
        public void unexpected() {
            throw new IllegalStateException("sensitive");
        }

        @GetMapping("/probe/resource")
        public void recursoInexistente() throws NoResourceFoundException {
            throw new NoResourceFoundException(org.springframework.http.HttpMethod.GET, "sensitive");
        }

        @PostMapping("/probe/body")
        public void body(@Valid @RequestBody ProbeRequest request) {
        }

        @GetMapping("/probe/constraint")
        public void constraint() {
            try (var factory = Validation.buildDefaultValidatorFactory()) {
                var violations = factory.getValidator().validate(new ProbeRequest(""));
                throw new jakarta.validation.ConstraintViolationException(violations);
            }
        }
    }
}
