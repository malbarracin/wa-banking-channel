package com.bcra.channel.ws.exception;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Set;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.constraints.NotBlank;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

class GlobalExceptionHandlerEdgeCasesTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new EdgeProbeController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("VALIDACIÓN: ConstraintViolationException → 400 VALIDATION_ERROR con el campo")
    void constraintViolationReturnsValidationError() throws Exception {
        mockMvc.perform(get("/edge/constraint"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.details[0].field").value("name"))
                .andExpect(jsonPath("$.details[0].message").isNotEmpty())
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    @DisplayName("BAD_REQUEST: parámetro con tipo inválido → 400")
    void typeMismatchReturnsBadRequest() throws Exception {
        mockMvc.perform(get("/edge/typed").param("count", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.details").isEmpty());
    }

    @Test
    @DisplayName("BAD_REQUEST: IllegalArgumentException → 400 sin exponer el mensaje interno")
    void illegalArgumentReturnsBadRequestWithoutInternals() throws Exception {
        mockMvc.perform(get("/edge/illegal"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
                .andExpect(content().string(not(containsString("internal-hint"))));
    }

    @Test
    @DisplayName("INTERNAL_ERROR: el cuerpo no lleva nombre de excepción, trazas ni el mensaje interno")
    void internalErrorBodyHasNoTrace() throws Exception {
        mockMvc.perform(get("/edge/npe"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(content().string(not(containsString("NullPointerException"))))
                .andExpect(content().string(not(containsString("at com.bcra"))))
                .andExpect(content().string(not(containsString("db-password"))));
    }

    record NamedInput(@NotBlank String name) {
    }

    @RestController
    static class EdgeProbeController {

        private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

        @GetMapping("/edge/constraint")
        void constraint() {
            Set<ConstraintViolation<NamedInput>> violations = validator.validate(new NamedInput(""));
            throw new ConstraintViolationException(violations);
        }

        @GetMapping("/edge/typed")
        void typed(@RequestParam int count) {
            // solo exige un entero
        }

        @GetMapping("/edge/illegal")
        void illegal() {
            throw new IllegalArgumentException("internal-hint");
        }

        @GetMapping("/edge/npe")
        void npe() {
            throw new NullPointerException("db-password");
        }
    }
}
