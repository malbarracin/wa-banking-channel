package com.bcra.insurance.ws.exception;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.fasterxml.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ErrorContractTest {

    @ParameterizedTest
    @CsvSource({
        "/probe/unauthorized,401,UNAUTHORIZED,Credencial de sesión inválida",
        "/probe/forbidden,403,FORBIDDEN,No tiene autorización para acceder al recurso"
    })
    @DisplayName("Los errores de acceso conservan el mensaje y los cuatro campos del contrato")
    void conservaElContratoDeAcceso(String path, int httpStatus, String code, String message) throws Exception {
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new GlobalExceptionHandlerTest.ErrorProbeController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        Instant inicio = Instant.now();

        String respuesta = mockMvc.perform(get(path))
                .andExpect(status().is(httpStatus))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value(code))
                .andExpect(jsonPath("$.message").value(message))
                .andExpect(jsonPath("$.details").isEmpty())
                .andReturn().getResponse().getContentAsString();

        var cuerpo = new ObjectMapper().readTree(respuesta);
        List<String> campos = new ArrayList<>();
        cuerpo.fieldNames().forEachRemaining(campos::add);
        assertThat(campos).containsExactlyInAnyOrder("code", "message", "details", "timestamp");
        assertThat(Instant.parse(cuerpo.get("timestamp").asText())).isBetween(inicio, Instant.now());
    }

    @Test
    @DisplayName("Modificar los detalles originales no altera la respuesta de error")
    void conservaLosDetallesAnteCambiosEnLaListaOriginal() {
        var detalle = new FieldError("nombre", "El nombre es obligatorio");
        List<FieldError> detalles = new ArrayList<>(List.of(detalle));
        var respuesta = new ErrorResponse(ErrorCode.VALIDATION_ERROR, "Datos inválidos", detalles, Instant.now());

        detalles.clear();

        assertThat(respuesta.details()).containsExactly(detalle);
    }
}
