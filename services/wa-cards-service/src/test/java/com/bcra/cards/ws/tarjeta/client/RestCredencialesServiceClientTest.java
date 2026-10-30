package com.bcra.cards.ws.tarjeta.client;

import java.time.Duration;

import com.bcra.cards.ws.config.RestClientConfig;
import com.bcra.cards.ws.exception.UnauthorizedException;
import com.bcra.cards.ws.tarjeta.client.dto.ValidarCredencialRequest;
import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.web.client.RestClient;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RestCredencialesServiceClientTest {

    private static final String PATH = "/api/v1/credenciales/validar";
    private static final String RESPUESTA = """
            {"vigente":true,"numeroWhatsapp":"+5491111111111","documentoTipo":"DNI",
             "documentoNumero":"12345678","fechaExpiracion":"2099-01-01T00:00:00Z"}
            """;

    private WireMockServer servidor;
    private CredencialesServiceClient cliente;

    @BeforeEach
    void configurar() {
        servidor = new WireMockServer(wireMockConfig().dynamicPort());
        servidor.start();
        var config = new RestClientConfig();
        cliente = config.credencialesServiceClient(config.credencialesRestClient(
                RestClient.builder(), servidor.baseUrl(), Duration.ofSeconds(1), Duration.ofSeconds(2)));
    }

    @AfterEach
    void detenerServidor() {
        servidor.stop();
    }

    @Test
    @DisplayName("Inyecta la URL y los timeouts desde las propiedades configurables")
    void cargaPropiedadesDelCliente() {
        responder(200, RESPUESTA);
        new ApplicationContextRunner().withUserConfiguration(RestClientConfig.class)
                .withInitializer(context -> context.getBeanFactory().setConversionService(
                        org.springframework.boot.convert.ApplicationConversionService.getSharedInstance()))
                .withBean(RestClient.Builder.class, RestClient::builder)
                .withPropertyValues("clients.wa-credentials-service.base-url=" + servidor.baseUrl(),
                        "clients.wa-credentials-service.connect-timeout=1s",
                        "clients.wa-credentials-service.read-timeout=2s")
                .run(context -> {
                    assertThat(context).hasNotFailed().hasSingleBean(CredencialesServiceClient.class);
                    assertThat(context.getBean(CredencialesServiceClient.class)
                            .validar("jwt-interno").vigente()).isTrue();
                });
    }

    @Test
    @DisplayName("Envía la credencial por JSON y devuelve la identidad vigente sin exponer PII en toString")
    void validaIdentidadVigente() {
        responder(200, RESPUESTA);

        var respuesta = cliente.validar("jwt-interno");

        assertThat(respuesta.vigente()).isTrue();
        assertThat(respuesta.numeroWhatsapp()).isEqualTo("+5491111111111");
        assertThat(respuesta.documentoTipo()).isEqualTo("DNI");
        assertThat(respuesta.documentoNumero()).isEqualTo("12345678");
        assertThat(respuesta.fechaExpiracion()).hasToString("2099-01-01T00:00:00Z");
        assertThat(respuesta.toString()).doesNotContain("12345678", "+5491111111111");
        assertThat(new ValidarCredencialRequest("jwt-interno").toString()).doesNotContain("jwt-interno");
        servidor.verify(postRequestedFor(urlEqualTo(PATH))
                .withRequestBody(equalToJson("{\"credencial\":\"jwt-interno\"}")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"expirada", "revocada", "invalida"})
    @DisplayName("Rechaza credenciales expiradas, revocadas o inválidas según el 401 de H2")
    void rechazaCredencial(String motivo) {
        responder(401, "{\"message\":\"" + motivo + "\"}");

        assertThatThrownBy(() -> cliente.validar("jwt-interno"))
                .isInstanceOf(UnauthorizedException.class).hasMessage("Credencial no válida");
    }

    @Test
    @DisplayName("Consulta H2 nuevamente y observa una revocación sin caché")
    void observaRevocacionInmediata() {
        responder(200, RESPUESTA);
        cliente.validar("jwt-interno");
        responder(401, "{}");

        assertThatThrownBy(() -> cliente.validar("jwt-interno")).isInstanceOf(UnauthorizedException.class);
        servidor.verify(2, postRequestedFor(urlEqualTo(PATH)));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" "})
    @DisplayName("Rechaza una credencial ausente sin llamar a H2")
    void rechazaCredencialAusente(String credencial) {
        assertThatThrownBy(() -> cliente.validar(credencial)).isInstanceOf(UnauthorizedException.class);
        assertThat(servidor.getAllServeEvents()).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "no-json", "{\"vigente\":true}",
        "{\"vigente\":true,\"numeroWhatsapp\":\" \"}", "{\"vigente\":true,\"numeroWhatsapp\":\"+5491111111111\"}"})
    @DisplayName("Una respuesta vacía, malformada o incompleta impide autorizar")
    void rechazaRespuestaIncompleta(String cuerpo) {
        responder(200, cuerpo);

        assertThatThrownBy(() -> cliente.validar("jwt-interno"))
                .isInstanceOf(CredencialesServiceUnavailableException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"vigente", "fechaExpiracion"})
    @DisplayName("Rechaza respuestas 200 no vigentes o con vencimiento pasado")
    void rechazaSesionVencida(String campo) {
        responder(200, campo.equals("vigente") ? RESPUESTA.replace("true", "false")
                : RESPUESTA.replace("2099", "2000"));

        assertThatThrownBy(() -> cliente.validar("jwt-interno")).isInstanceOf(UnauthorizedException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"numeroWhatsapp", "documentoTipo", "documentoNumero", "fechaExpiracion"})
    @DisplayName("No autoriza si H2 omite cualquier dato de identidad o el vencimiento")
    void rechazaCadaCampoObligatorioAusente(String campo) {
        String cuerpo = RESPUESTA.replaceAll("\"" + campo + "\":\"[^\"]*\"", "\"" + campo + "\":null");
        responder(200, cuerpo);

        assertThatThrownBy(() -> cliente.validar("jwt-interno"))
                .isInstanceOf(CredencialesServiceUnavailableException.class);
        servidor.verify(1, postRequestedFor(urlEqualTo(PATH)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"DNI", "12345678"})
    @DisplayName("No autoriza una identidad con tipo o número documental en blanco")
    void rechazaDocumentoEnBlanco(String valor) {
        responder(200, RESPUESTA.replace(valor, " "));

        assertThatThrownBy(() -> cliente.validar("jwt-interno"))
                .isInstanceOf(CredencialesServiceUnavailableException.class);
    }

    @Test
    @DisplayName("No autoriza si H2 devuelve una fecha de expiración ilegible")
    void rechazaVencimientoMalformado() {
        responder(200, RESPUESTA.replace("2099-01-01T00:00:00Z", "fecha-invalida"));

        assertThatThrownBy(() -> cliente.validar("jwt-interno"))
                .isInstanceOf(CredencialesServiceUnavailableException.class);
    }

    @ParameterizedTest
    @ValueSource(ints = {400, 403, 429, 500, 503})
    @DisplayName("Los errores HTTP impiden validar sin exponer el cuerpo remoto")
    void rechazaErrorRemoto(int estado) {
        responder(estado, "detalle-sensible");

        assertThatThrownBy(() -> cliente.validar("jwt-interno"))
                .isInstanceOf(CredencialesServiceUnavailableException.class)
                .hasMessageNotContaining("detalle-sensible");
    }

    @Test
    @DisplayName("Aplica el timeout de lectura configurado y no autoriza ante demora de H2")
    void aplicaTimeoutLectura() {
        var config = new RestClientConfig();
        cliente = config.credencialesServiceClient(config.credencialesRestClient(
                RestClient.builder(), servidor.baseUrl(), Duration.ofSeconds(1), Duration.ofMillis(100)));
        servidor.stubFor(post(urlEqualTo(PATH)).willReturn(aResponse().withHeader("Content-Type", "application/json")
                .withBody(RESPUESTA).withFixedDelay(700)));

        assertThatThrownBy(() -> cliente.validar("jwt-interno"))
                .isInstanceOf(CredencialesServiceUnavailableException.class)
                .hasCauseInstanceOf(org.springframework.web.client.RestClientException.class)
                .hasRootCauseInstanceOf(java.net.SocketTimeoutException.class);
    }

    private void responder(int estado, String cuerpo) {
        servidor.stubFor(post(urlEqualTo(PATH)).willReturn(aResponse().withStatus(estado)
                .withHeader("Content-Type", "application/json").withBody(cuerpo)));
    }
}
