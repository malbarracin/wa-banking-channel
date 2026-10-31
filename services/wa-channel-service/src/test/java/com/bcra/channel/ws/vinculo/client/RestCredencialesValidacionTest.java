package com.bcra.channel.ws.vinculo.client;

import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

@DisplayName("Validación autoritativa de sesiones con H2")
class RestCredencialesValidacionTest {

    private static final String NUMERO = "+5491199990020";
    private MockRestServiceServer servidor;
    private RestCredencialesServiceClient cliente;

    @BeforeEach
    void preparar() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://h2");
        servidor = MockRestServiceServer.bindTo(builder).build();
        cliente = new RestCredencialesServiceClient(builder.build());
    }

    @Test
    @DisplayName("Acepta solamente una sesión vigente validada por H2 para el número solicitado")
    void aceptaSesionValidada() {
        servidor.expect(requestTo("http://h2/api/v1/credenciales/validar"))
                .andExpect(content().json("{\"credencial\":\"sesion-h2\"}"))
                .andRespond(withSuccess(respuesta(NUMERO, Instant.now().plusSeconds(300)), MediaType.APPLICATION_JSON));
        assertThat(cliente.validar("sesion-h2", NUMERO)).isTrue();
        servidor.verify();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" "})
    @DisplayName("Sin token no consulta H2 ni autoriza")
    void rechazaTokenAusente(String token) {
        assertThat(cliente.validar(token, NUMERO)).isFalse();
        servidor.verify();
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "null", "{\"vigente\":false}", "{", "{\"vigente\":true}"})
    @DisplayName("Respuestas incompletas o ilegibles no autorizan")
    void rechazaRespuestaInvalida(String cuerpo) {
        servidor.expect(requestTo("http://h2/api/v1/credenciales/validar"))
                .andRespond(withSuccess(cuerpo, MediaType.APPLICATION_JSON));
        assertThat(cliente.validar("sesion-h2", NUMERO)).isFalse();
    }

    @Test
    @DisplayName("Una sesión válida para otro número no autoriza")
    void rechazaOtroTitular() {
        servidor.expect(requestTo("http://h2/api/v1/credenciales/validar"))
                .andRespond(withSuccess(respuesta("+5491100000000", Instant.now().plusSeconds(300)),
                        MediaType.APPLICATION_JSON));
        assertThat(cliente.validar("sesion-h2", NUMERO)).isFalse();
    }

    @Test
    @DisplayName("Una sesión vencida no autoriza aunque H2 informe vigente")
    void rechazaSesionVencida() {
        servidor.expect(requestTo("http://h2/api/v1/credenciales/validar"))
                .andRespond(withSuccess(respuesta(NUMERO, Instant.now().minusSeconds(1)), MediaType.APPLICATION_JSON));
        assertThat(cliente.validar("sesion-h2", NUMERO)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(ints = {401, 500, 503})
    @DisplayName("Token adulterado, revocado o H2 indisponible no autorizan")
    void rechazaErrorH2(int status) {
        servidor.expect(requestTo("http://h2/api/v1/credenciales/validar"))
                .andRespond(withStatus(HttpStatus.valueOf(status)));
        assertThat(cliente.validar("true", NUMERO)).isFalse();
    }

    private String respuesta(String numero, Instant vencimiento) {
        return "{\"vigente\":true,\"numeroWhatsapp\":\"" + numero
                + "\",\"documentoTipo\":\"DNI\",\"documentoNumero\":\"12345678\",\"fechaExpiracion\":\""
                + vencimiento + "\"}";
    }
}
