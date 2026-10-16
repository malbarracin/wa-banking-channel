package com.bank.users.exception;

import java.time.Instant;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GlobalExceptionHandlerBoundaryTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private MockMvc mockMvc;

    @BeforeEach
    void configurarMvc() {
        mockMvc = MockMvcBuilders.standaloneSetup(new BoundaryController())
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @Test
    @DisplayName("Una respuesta inválida del servidor devuelve 500 con mensaje genérico")
    void devuelveErrorInternoCuandoElRetornoNoCumpleValidacion() throws Exception {
        Instant inicio = Instant.now();

        var respuesta = mockMvc.perform(get("/api/v1/contract/invalid-return"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.message").value("Error interno"))
                .andExpect(jsonPath("$.details").isEmpty())
                .andReturn().getResponse();

        JsonNode cuerpo = objectMapper.readTree(respuesta.getContentAsByteArray());
        assertThat(cuerpo.size()).isEqualTo(4);
        assertThat(Instant.parse(cuerpo.get("timestamp").asText())).isBetween(inicio, Instant.now());
        assertThat(cuerpo.toString()).doesNotContain("PII_SECRETA", "BoundaryController", "stackTrace");
    }

    @Test
    @DisplayName("La validación informa cada campo sin publicar mensajes sensibles de las restricciones")
    void informaTodosLosCamposInvalidosSinDatosSensibles() throws Exception {
        var respuesta = mockMvc.perform(post("/api/v1/contract/fields")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andReturn().getResponse();

        JsonNode cuerpo = objectMapper.readTree(respuesta.getContentAsByteArray());
        Set<String> campos = StreamSupport.stream(cuerpo.get("details").spliterator(), false)
                .map(detalle -> detalle.get("field").asText()).collect(Collectors.toSet());
        assertThat(campos).containsExactlyInAnyOrder("nombre", "contacto");
        assertThat(cuerpo.get("details").size()).isEqualTo(2);
        assertThat(cuerpo.toString()).doesNotContain("PII_SECRETA", "rejectedValue", "stackTrace");
    }

    @Test
    @DisplayName("Un cuerpo ausente devuelve BAD_REQUEST con detalles vacíos")
    void rechazaCuerpoAusenteConContratoUniforme() throws Exception {
        mockMvc.perform(post("/api/v1/contract/fields").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.details").isEmpty())
                .andExpect(jsonPath("$.timestamp").isNotEmpty());
    }

    record CamposRequest(@NotBlank(message = "PII_SECRETA") String nombre,
            @NotBlank(message = "PII_SECRETA") String contacto) {
    }

    @RestController
    static class BoundaryController {

        @GetMapping("/api/v1/contract/invalid-return")
        @Min(value = 1, message = "PII_SECRETA")
        int devolverRetornoInvalido() {
            return 0;
        }

        @PostMapping("/api/v1/contract/fields")
        CamposRequest validarCampos(@Valid @RequestBody CamposRequest solicitud) {
            return solicitud;
        }
    }
}
