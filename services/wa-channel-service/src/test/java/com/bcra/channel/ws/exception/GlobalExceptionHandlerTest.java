package com.bcra.channel.ws.exception;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.resource.NoResourceFoundException;

class GlobalExceptionHandlerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ProbeController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("VALIDACIÓN: 400 VALIDATION_ERROR con detalles de campo")
    void returnsValidationError() throws Exception {
        mockMvc.perform(post("/probe/validate").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.details[0].field").value("name"))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.stackTrace").doesNotExist());
    }

    @Test
    @DisplayName("BAD_REQUEST: 400 por cuerpo mal formado")
    void returnsBadRequest() throws Exception {
        mockMvc.perform(post("/probe/validate").contentType(MediaType.APPLICATION_JSON).content("{"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.details").isArray())
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.stackTrace").doesNotExist());
    }

    @Test
    @DisplayName("BAD_REQUEST: 400 por parámetro obligatorio faltante")
    void returnsBadRequestWhenRequiredParamMissing() throws Exception {
        mockMvc.perform(get("/probe/required-param"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.details").isArray())
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    @DisplayName("BAD_REQUEST: 400 por header obligatorio faltante")
    void returnsBadRequestWhenRequiredHeaderMissing() throws Exception {
        mockMvc.perform(get("/probe/required-header"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.details").isArray())
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.stackTrace").doesNotExist());
    }

    @Test
    @DisplayName("FORBIDDEN: 403")
    void returnsForbidden() throws Exception {
        mockMvc.perform(get("/probe/forbidden"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.details").isArray())
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.stackTrace").doesNotExist());
    }

    @Test
    @DisplayName("NOT_FOUND: 404")
    void returnsNotFound() throws Exception {
        mockMvc.perform(get("/probe/not-found"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.details").isArray())
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.stackTrace").doesNotExist());
    }

    @Test
    @DisplayName("NOT_FOUND: 404 por ruta inexistente (NoResourceFoundException)")
    void returnsNotFoundWhenResourceMissing() throws Exception {
        mockMvc.perform(get("/probe/no-resource"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.details").isArray())
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.stackTrace").doesNotExist());
    }

    @Test
    @DisplayName("BAD_REQUEST: 405 por método HTTP no soportado")
    void returnsMethodNotAllowed() throws Exception {
        mockMvc.perform(post("/probe/forbidden"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.details").isArray())
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.stackTrace").doesNotExist());
    }

    @Test
    @DisplayName("BAD_REQUEST: 415 por tipo de contenido no soportado")
    void returnsUnsupportedMediaType() throws Exception {
        mockMvc.perform(post("/probe/validate").contentType(MediaType.TEXT_PLAIN).content("name=x"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.details").isArray())
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.stackTrace").doesNotExist());
    }

    @Test
    @DisplayName("CONFLICT: 409")
    void returnsConflict() throws Exception {
        mockMvc.perform(get("/probe/conflict"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"))
                .andExpect(jsonPath("$.details").isArray())
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.stackTrace").doesNotExist());
    }

    @Test
    @DisplayName("VERIFICATION_LOCKED: 423")
    void returnsVerificationLocked() throws Exception {
        mockMvc.perform(get("/probe/verification-locked"))
                .andExpect(status().isLocked())
                .andExpect(jsonPath("$.code").value("VERIFICATION_LOCKED"))
                .andExpect(jsonPath("$.details").isArray())
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.stackTrace").doesNotExist());
    }

    @Test
    @DisplayName("INTERNAL_ERROR: 500 sin stacktrace ni detalle interno")
    void returnsInternalErrorWithoutStacktrace() throws Exception {
        mockMvc.perform(get("/probe/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.message", not(containsString("secret-detail"))))
                .andExpect(jsonPath("$.details").isArray())
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.stackTrace").doesNotExist());
    }

    record ValidateRequest(@NotBlank String name) {
    }

    static final class ProbeNotFoundException extends NotFoundException {
        ProbeNotFoundException() {
            super("Recurso no encontrado");
        }
    }

    static final class ProbeConflictException extends ConflictException {
        ProbeConflictException() {
            super("Conflicto de estado");
        }
    }

    @RestController
    static class ProbeController {

        @PostMapping("/probe/validate")
        void validate(@Valid @RequestBody ValidateRequest request) {
            // solo valida el cuerpo
        }

        @GetMapping("/probe/forbidden")
        void forbidden() {
            throw new ForbiddenException("Rol interno no autorizado");
        }

        @GetMapping("/probe/not-found")
        void notFound() {
            throw new ProbeNotFoundException();
        }

        @GetMapping("/probe/no-resource")
        void noResource() throws NoResourceFoundException {
            throw new NoResourceFoundException(HttpMethod.GET, "/api/v1/ruta-inexistente");
        }

        @GetMapping("/probe/conflict")
        void conflict() {
            throw new ProbeConflictException();
        }

        @GetMapping("/probe/verification-locked")
        void verificationLocked() {
            throw new VerificationLockedException("Intentos de verificación agotados");
        }

        @GetMapping("/probe/required-param")
        void requiredParam(@RequestParam String value) {
            // solo exige el parámetro
        }

        @GetMapping("/probe/required-header")
        void requiredHeader(@RequestHeader("X-Internal-Role") String role) {
            // solo exige el header de rol interno
        }

        @GetMapping("/probe/boom")
        void boom() {
            throw new IllegalStateException("secret-detail");
        }
    }
}
