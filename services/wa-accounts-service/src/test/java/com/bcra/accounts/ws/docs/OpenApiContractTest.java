package com.bcra.accounts.ws.docs;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class OpenApiContractTest {

    private final MockMvc mockMvc;
    private final ObjectMapper objectMapper;

    @Autowired
    OpenApiContractTest(MockMvc mockMvc, ObjectMapper objectMapper) {
        this.mockMvc = mockMvc;
        this.objectMapper = objectMapper;
    }

    @ParameterizedTest(name = "{1} {0}")
    @CsvSource({
        "/api/v1/cuentas, get, 200 401 500",
        "/api/v1/cuentas/{numeroCuenta}/saldo, get, 200 401 404 500",
        "/api/v1/cuentas/{numero}/movimientos, get, 200 400 401 404 500",
        "/api/v1/terceros, get, 200 401 500",
        "/api/v1/transferencias, post, 201 400 401 404 409 500",
        "/api/v1/transferencias/{id}/confirmar, post, 200 400 401 404 409 500"
    })
    @DisplayName("Cada operación documenta sus respuestas y ejemplos con el contrato de error común")
    void documentaRespuestasYErroresPorOperacion(String ruta, String metodo, String estados) throws Exception {
        String json = mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        JsonNode documento = objectMapper.readTree(json);
        JsonNode operacion = documento.path("paths").path(ruta).path(metodo);

        List<String> parametros = new ArrayList<>();
        operacion.path("parameters").forEach(parametro -> parametros.add(
                parametro.path("in").asText() + ":" + parametro.path("name").asText()));
        assertThat(parametros).contains("header:X-Credencial-Sesion");
        assertThat(operacion.path("responses").properties()).extracting(java.util.Map.Entry::getKey)
                .containsExactlyInAnyOrder(estados.split(" "));

        for (String estado : estados.split(" ")) {
            JsonNode respuesta = operacion.path("responses").path(estado);
            assertThat(respuesta.path("description").asText()).isNotBlank();
        }
        for (String estado : estados.replaceFirst("^2\\d{2} ", "").split(" ")) {
            verificarError(documento, operacion.path("responses").path(estado), estado);
        }
    }

    private void verificarError(JsonNode documento, JsonNode respuesta, String estado) {
        JsonNode contenido = respuesta.path("content").path("application/json");
        assertThat(contenido.path("schema").path("$ref").asText())
                .isEqualTo("#/components/schemas/ErrorResponse");
        assertThat(contenido.path("examples").size()).isPositive();
        for (JsonNode referencia : contenido.path("examples")) {
            JsonNode ejemplo = documento.at(referencia.path("$ref").asText().substring(1)).path("value");
            assertThat(ejemplo.path("message").asText()).isNotBlank();
            assertThat(ejemplo.path("details").isArray()).isTrue();
            assertThat(Instant.parse(ejemplo.path("timestamp").asText())).isNotNull();
            assertThat(ejemplo.properties()).extracting(java.util.Map.Entry::getKey)
                    .containsExactlyInAnyOrder("code", "message", "details", "timestamp");
            List<String> codigos = switch (estado) {
                case "400" -> List.of("BAD_REQUEST", "VALIDATION_ERROR");
                case "401" -> List.of("UNAUTHORIZED");
                case "404" -> List.of("NOT_FOUND");
                case "409" -> List.of("CONFLICT");
                case "500" -> List.of("INTERNAL_ERROR");
                default -> throw new IllegalArgumentException("Estado sin contrato: " + estado);
            };
            assertThat(ejemplo.path("code").asText()).isIn(codigos);
        }
    }
}
