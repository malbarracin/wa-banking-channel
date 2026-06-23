package com.wa.banking.accounts.api.error;

import com.wa.banking.accounts.support.AbstractMongoIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GlobalExceptionHandlerIT extends AbstractMongoIntegrationTest {

    private static final String UNAUTHORIZED_MESSAGE =
            "Por seguridad, verificá tu identidad para ver tus cuentas.";

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("Should return VALIDATION_ERROR contract on bean validation failure")
    void shouldReturnValidationErrorContract_whenRequestIsInvalid() throws Exception {
        mockMvc.perform(post("/api/test/errors/validation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value("Validation failed"))
                .andExpect(jsonPath("$.details").isArray())
                .andExpect(jsonPath("$.details[0]").value("name: name is required"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    @DisplayName("Should return BAD_REQUEST contract on illegal argument")
    void shouldReturnBadRequestContract_whenIllegalArgumentThrown() throws Exception {
        mockMvc.perform(get("/api/test/errors/bad-request"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.message").value("Invalid parameter"))
                .andExpect(jsonPath("$.details").isArray())
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    @DisplayName("Should return NOT_FOUND contract on missing account")
    void shouldReturnNotFoundContract_whenAccountNotFound() throws Exception {
        mockMvc.perform(get("/api/test/errors/not-found"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Account not found: acc-missing"))
                .andExpect(jsonPath("$.details").isArray())
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    @DisplayName("Should return UNAUTHORIZED contract from security filter")
    void shouldReturnUnauthorizedContract_whenAuthHeadersMissing() throws Exception {
        mockMvc.perform(get("/api/v1/accounts"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.message").value(UNAUTHORIZED_MESSAGE))
                .andExpect(jsonPath("$.details").isArray())
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    @DisplayName("Should return UNAUTHORIZED contract from exception handler")
    void shouldReturnUnauthorizedContract_whenUnauthorizedExceptionThrown() throws Exception {
        mockMvc.perform(get("/api/test/errors/unauthorized"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.message").value("Invalid session credential"))
                .andExpect(jsonPath("$.details").isArray())
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    @DisplayName("Should return INTERNAL_ERROR contract on unexpected failure")
    void shouldReturnInternalErrorContract_whenUnexpectedExceptionThrown() throws Exception {
        mockMvc.perform(get("/api/test/errors/internal"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.message").value("An unexpected error occurred"))
                .andExpect(jsonPath("$.details").isArray())
                .andExpect(jsonPath("$.timestamp").exists());
    }
}
