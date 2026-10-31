package com.bcra.accounts.ws.cuenta.client;

import java.io.IOException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.bcra.accounts.ws.config.RestClientConfig;
import com.bcra.accounts.ws.cuenta.client.dto.ValidarCredencialRequest;
import com.bcra.accounts.ws.exception.UnauthorizedException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class RestCredencialesServiceClientTest {

    private static final String URL = "http://h2.example/api/v1/credenciales/validar";
    private static final String RESPUESTA = """
            {"vigente":true,"numeroWhatsapp":"+5491111111111","documentoTipo":"DNI",
             "documentoNumero":"12345678","fechaExpiracion":"2099-01-01T00:00:00Z"}
            """;

    private MockRestServiceServer servidor;
    private CredencialesServiceClient cliente;

    @BeforeEach
    void configurar() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://h2.example");
        servidor = MockRestServiceServer.bindTo(builder).build();
        cliente = new RestClientConfig().credencialesServiceClient(builder.build());
    }

    @Test
    @DisplayName("Envía el JWT como JSON y devuelve la identidad vigente de H2")
    void validaCredencialConContratoH2() {
        servidor.expect(requestTo(URL)).andExpect(method(HttpMethod.POST))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().json("{\"credencial\":\"jwt-interno\"}"))
                .andRespond(withSuccess(RESPUESTA, MediaType.APPLICATION_JSON));

        var respuesta = cliente.validar("jwt-interno");

        assertThat(respuesta.vigente()).isTrue();
        assertThat(respuesta.numeroWhatsapp()).isEqualTo("+5491111111111");
        assertThat(respuesta.documentoTipo()).isEqualTo("DNI");
        assertThat(respuesta.documentoNumero()).isEqualTo("12345678");
        assertThat(respuesta.fechaExpiracion()).hasToString("2099-01-01T00:00:00Z");
        assertThat(respuesta.toString()).doesNotContain("12345678", "+5491111111111");
        assertThat(new ValidarCredencialRequest("jwt-interno").toString()).doesNotContain("jwt-interno");
        servidor.verify();
    }

    @Test
    @DisplayName("Una revocación en H2 se observa en la siguiente validación sin caché")
    void consultaH2EnCadaOperacion() {
        servidor.expect(requestTo(URL)).andRespond(withSuccess(RESPUESTA, MediaType.APPLICATION_JSON));
        servidor.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        cliente.validar("jwt-interno");

        assertThatThrownBy(() -> cliente.validar("jwt-interno")).isInstanceOf(UnauthorizedException.class);
        servidor.verify();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" "})
    @DisplayName("Rechaza credenciales ausentes sin invocar H2")
    void rechazaCredencialAusente(String credencial) {
        assertThatThrownBy(() -> cliente.validar(credencial)).isInstanceOf(UnauthorizedException.class);
        servidor.verify();
    }

    @ParameterizedTest
    @ValueSource(ints = {400, 403, 404, 429, 500, 503, 302})
    @DisplayName("Los errores HTTP de H2 impiden validar sin exponer el cuerpo remoto")
    void rechazaErroresHttp(int estado) {
        servidor.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.valueOf(estado))
                .body("detalle-sensible"));

        assertThatThrownBy(() -> cliente.validar("jwt-interno"))
                .isInstanceOf(CredencialesServiceUnavailableException.class)
                .hasMessageNotContaining("detalle-sensible");
        servidor.verify();
    }

    @Test
    @DisplayName("Una falla de transporte impide validar y conserva la causa")
    void rechazaFallaDeTransporte() {
        servidor.expect(requestTo(URL)).andRespond(withException(new IOException("timeout")));

        assertThatThrownBy(() -> cliente.validar("jwt-interno"))
                .isInstanceOf(CredencialesServiceUnavailableException.class).hasCauseInstanceOf(
                        org.springframework.web.client.ResourceAccessException.class);
        servidor.verify();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "no-json", "{}", "{\"vigente\":true}",
        "{\"vigente\":true,\"fechaExpiracion\":\"invalida\"}",
        "{\"vigente\":true,\"numeroWhatsapp\":\"+5491111111111\",\"documentoTipo\":\"DNI\"}",
        "{\"vigente\":true,\"numeroWhatsapp\":\"+5491111111111\",\"documentoTipo\":\"DNI\","
            + "\"documentoNumero\":\"12345678\"}"})
    @DisplayName("Una respuesta vacía o incompleta nunca autoriza operaciones")
    void rechazaRespuestaIncompleta(String respuesta) {
        servidor.expect(requestTo(URL)).andRespond(withSuccess(respuesta, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> cliente.validar("jwt-interno"))
                .isInstanceOfAny(CredencialesServiceUnavailableException.class, UnauthorizedException.class);
        servidor.verify();
    }

    @ParameterizedTest
    @ValueSource(strings = {"numeroWhatsapp", "documentoTipo", "documentoNumero"})
    @DisplayName("Una identidad vigente sin un campo obligatorio se trata como falla de H2")
    void rechazaIdentidadIncompleta(String campo) {
        String respuesta = RESPUESTA.replaceAll("\"" + campo + "\":\"[^\"]*\"", "\"" + campo + "\":null");
        servidor.expect(requestTo(URL)).andRespond(withSuccess(respuesta, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> cliente.validar("jwt-interno"))
                .isInstanceOf(CredencialesServiceUnavailableException.class);
        servidor.verify();
    }

    @ParameterizedTest
    @ValueSource(strings = {"numeroWhatsapp", "documentoTipo", "documentoNumero"})
    @DisplayName("Una identidad vigente con campos en blanco nunca autoriza la operación")
    void rechazaIdentidadConCamposEnBlanco(String campo) {
        String respuesta = RESPUESTA.replaceAll("\"" + campo + "\":\"[^\"]*\"", "\"" + campo + "\":\" \"");
        servidor.expect(requestTo(URL)).andRespond(withSuccess(respuesta, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> cliente.validar("jwt-interno"))
                .isInstanceOf(CredencialesServiceUnavailableException.class);
        servidor.verify();
    }

    @ParameterizedTest
    @ValueSource(strings = {"no-vigente", "expirada"})
    @DisplayName("Rechaza sesiones no vigentes o vencidas aunque H2 responda 200")
    void rechazaSesionNoVigente(String caso) {
        String respuesta = "no-vigente".equals(caso) ? RESPUESTA.replace("true", "false")
                : RESPUESTA.replace("2099-01-01", "2000-01-01");
        servidor.expect(requestTo(URL)).andRespond(withSuccess(respuesta, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> cliente.validar("jwt-interno")).isInstanceOf(UnauthorizedException.class);
        servidor.verify();
    }
}
