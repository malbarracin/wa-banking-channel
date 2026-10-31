package com.bcra.cards.ws.exception;

import java.time.Instant;
import java.util.stream.Stream;

import com.bcra.cards.ws.tarjeta.exception.BloqueoDefinitivoIrreversibleException;
import com.bcra.cards.ws.tarjeta.exception.BloqueoNoAplicableException;
import com.bcra.cards.ws.tarjeta.exception.ConfirmacionRequeridaException;
import com.bcra.cards.ws.tarjeta.exception.TarjetaNoAutorizadaException;
import com.bcra.cards.ws.tarjeta.exception.TarjetaNoEncontradaException;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TarjetaExceptionHandlerTest {

    @Test
    @DisplayName("Una tarjeta ajena y una inexistente devuelven el mismo error público")
    void ocultaLaExistenciaDeTarjetasAjenas() throws Exception {
        var mapper = new ObjectMapper();
        var handler = new GlobalExceptionHandler();
        var inexistente = MockMvcBuilders.standaloneSetup(
                new TarjetaErrorController(new TarjetaNoEncontradaException()))
                .setControllerAdvice(handler).build();
        var ajena = MockMvcBuilders.standaloneSetup(
                new TarjetaErrorController(new TarjetaNoAutorizadaException()))
                .setControllerAdvice(handler).build();

        var respuestaInexistente = inexistente.perform(get("/probe/tarjeta"))
                .andExpect(status().isNotFound()).andReturn().getResponse();
        var respuestaAjena = ajena.perform(get("/probe/tarjeta"))
                .andExpect(status().isNotFound()).andReturn().getResponse();
        var errorInexistente = mapper.readTree(respuestaInexistente.getContentAsString());
        var errorAjena = mapper.readTree(respuestaAjena.getContentAsString());

        for (String campo : new String[] {"code", "message", "details"}) {
            assertThat(errorAjena.get(campo)).isEqualTo(errorInexistente.get(campo));
        }
    }

    @ParameterizedTest
    @MethodSource("erroresDeDominio")
    @DisplayName("Cada excepción de Tarjetas conserva el contrato HTTP sin exponer la tarjeta")
    void respondeErroresDeDominio(RuntimeException exception, int httpStatus, String code) throws Exception {
        var mockMvc = MockMvcBuilders.standaloneSetup(new TarjetaErrorController(exception))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        var response = mockMvc.perform(get("/probe/tarjeta"))
                .andExpect(status().is(httpStatus))
                .andExpect(jsonPath("$.code").value(code))
                .andExpect(jsonPath("$.details", hasSize(0)))
                .andExpect(jsonPath("$.timestamp").isString())
                .andExpect(jsonPath("$.trace").doesNotExist())
                .andExpect(jsonPath("$.exception").doesNotExist())
                .andReturn().getResponse();
        var body = new ObjectMapper().readTree(response.getContentAsString());
        assertThat(body.size()).isEqualTo(4);
        assertThat(body.get("message").asText()).isEqualTo(exception.getMessage());
        assertThat(Instant.parse(body.get("timestamp").asText())).isBeforeOrEqualTo(Instant.now());
    }

    static Stream<Arguments> erroresDeDominio() {
        return Stream.of(
                Arguments.of(new TarjetaNoEncontradaException(), 404, "NOT_FOUND"),
                Arguments.of(new TarjetaNoAutorizadaException(), 404, "NOT_FOUND"),
                Arguments.of(new BloqueoNoAplicableException(), 409, "CONFLICT"),
                Arguments.of(new BloqueoDefinitivoIrreversibleException(), 409, "CONFLICT"),
                Arguments.of(new ConfirmacionRequeridaException(), 409, "CONFLICT"));
    }

    @RestController
    static class TarjetaErrorController {

        private final RuntimeException exception;

        TarjetaErrorController(RuntimeException exception) {
            this.exception = exception;
        }

        @GetMapping("/probe/tarjeta")
        public void rechazaOperacion() {
            throw exception;
        }
    }
}
