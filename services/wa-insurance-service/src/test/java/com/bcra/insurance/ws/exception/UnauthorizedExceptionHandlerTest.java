package com.bcra.insurance.ws.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.bcra.insurance.ws.seguro.client.CredencialesServiceUnavailableException;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class UnauthorizedExceptionHandlerTest {

    @Test
    @DisplayName("La credencial rechazada usa el contrato único de error con HTTP 401")
    void respondeUnauthorized() throws Exception {
        var mockMvc = MockMvcBuilders.standaloneSetup(new CredencialProbeController())
                .setControllerAdvice(new GlobalExceptionHandler()).build();

        mockMvc.perform(get("/probe/credencial"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.message").value("Verificá tu identidad para consultar tus seguros."))
                .andExpect(jsonPath("$.details").isEmpty())
                .andExpect(jsonPath("$.timestamp").isString());
    }

    @Test
    @DisplayName("Una falla de H2 usa el error interno sin exponer su causa")
    void protegeFallaRemota() throws Exception {
        var mockMvc = MockMvcBuilders.standaloneSetup(new CredencialProbeController())
                .setControllerAdvice(new GlobalExceptionHandler()).build();

        mockMvc.perform(get("/probe/h2"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.message").value("Error interno"))
                .andExpect(jsonPath("$.details").isEmpty())
                .andExpect(jsonPath("$.timestamp").isString());
    }

    @RestController
    static class CredencialProbeController {

        @GetMapping("/probe/credencial")
        public void rechazar() {
            throw new UnauthorizedException("Verificá tu identidad para consultar tus seguros.");
        }

        @GetMapping("/probe/h2")
        public void fallar() {
            throw new CredencialesServiceUnavailableException(new IllegalStateException("dato sensible"));
        }
    }
}
