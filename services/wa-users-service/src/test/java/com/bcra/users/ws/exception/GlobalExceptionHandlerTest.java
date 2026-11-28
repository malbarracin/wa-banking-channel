package com.bcra.users.ws.exception;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@WebMvcTest(GlobalExceptionHandlerTest.TestController.class)
@Import({GlobalExceptionHandler.class, GlobalExceptionHandlerTest.TestController.class})
class GlobalExceptionHandlerTest {
    @Autowired
    private MockMvc mvc;

    @Test
    void mapsErrorsWithoutExposingInternalDetails() throws Exception {
        check("/bad", 400, "BAD_REQUEST");
        check("/missing", 404, "NOT_FOUND");
        check("/conflict", 409, "CONFLICT");
        check("/unexpected", 500, "INTERNAL_ERROR");
        mvc.perform(post("/validate").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.details[0].field").value("name"))
                .andExpect(jsonPath("$.timestamp").exists()).andExpect(jsonPath("$.trace").doesNotExist());
    }

    @Test
    void mapsConstraintViolationsToValidationErrors() throws Exception {
        mvc.perform(get("/parameter-validation").param("name", ""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.details[0].field").value("parameterValidation.name"))
                .andExpect(jsonPath("$.details[0].message").exists())
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void mapsMissingRequestParametersToBadRequest() throws Exception {
        check("/parameter-validation", 400, "BAD_REQUEST");
    }

    private void check(String path, int statusCode, String code) throws Exception {
        mvc.perform(get(path)).andExpect(status().is(statusCode)).andExpect(jsonPath("$.code").value(code))
                .andExpect(jsonPath("$.message").exists()).andExpect(jsonPath("$.details").isArray())
                .andExpect(jsonPath("$.timestamp").exists()).andExpect(jsonPath("$.trace").doesNotExist());
    }

    record Request(@NotBlank String name) {
    }

    @RestController
    @Validated
    static class TestController {
        @GetMapping("/parameter-validation")
        void parameterValidation(@RequestParam @NotBlank String name) {
        }

        @GetMapping("/bad")
        void bad() {
            throw new IllegalArgumentException("sensitive");
        }

        @GetMapping("/missing")
        void missing() {
            throw new NotFoundException("missing") { };
        }

        @GetMapping("/conflict")
        void conflict() {
            throw new ConflictException("conflict") { };
        }

        @GetMapping("/unexpected")
        void unexpected() {
            throw new IllegalStateException("sensitive");
        }

        @PostMapping("/validate")
        void validate(@Valid @RequestBody Request request) {
        }
    }
}
