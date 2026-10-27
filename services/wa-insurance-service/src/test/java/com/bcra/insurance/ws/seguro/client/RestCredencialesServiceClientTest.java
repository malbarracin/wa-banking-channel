package com.bcra.insurance.ws.seguro.client;

import java.time.Duration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.web.client.RestClientAutoConfiguration;
import org.springframework.boot.convert.ApplicationConversionService;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.web.client.RestClient;

import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;

import com.bcra.insurance.ws.config.RestClientConfig;
import com.bcra.insurance.ws.exception.UnauthorizedException;
import com.bcra.insurance.ws.seguro.client.dto.ValidarCredencialRequest;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@WireMockTest
class RestCredencialesServiceClientTest {

    private static final String PATH = "/api/v1/credenciales/validar";
    private static final String RESPUESTA = """
            {"vigente":true,"numeroWhatsapp":"+5491111111111","documentoTipo":"DNI",
             "documentoNumero":"12345678","fechaExpiracion":"2099-01-01T00:00:00Z"}
            """;

    private CredencialesServiceClient cliente;

    @BeforeEach
    void configurar(WireMockRuntimeInfo servidor) {
        // Timeouts como prod: 200ms en @BeforeEach provoca SocketTimeout flaky bajo carga de WireMock.
        cliente = crearCliente(servidor.getHttpBaseUrl(), Duration.ofSeconds(2), Duration.ofSeconds(3));
    }

    private static CredencialesServiceClient crearCliente(String baseUrl, Duration connectTimeout,
            Duration readTimeout) {
        var configuracion = new RestClientConfig();
        return configuracion.credencialesServiceClient(configuracion.credencialesRestClient(
                RestClient.builder(), baseUrl, connectTimeout, readTimeout));
    }

    @Test
    @DisplayName("Envía el JWT a H2 como JSON y obtiene la identidad sin exponer datos en toString")
    void validaConContratoH2() {
        stubFor(post(PATH).willReturn(aResponse().withHeader("Content-Type", "application/json")
                .withBody(RESPUESTA)));

        var respuesta = cliente.validar("jwt-interno");

        assertThat(respuesta.vigente()).isTrue();
        assertThat(respuesta.documentoTipo()).isEqualTo("DNI");
        assertThat(respuesta.documentoNumero()).isEqualTo("12345678");
        assertThat(respuesta.numeroWhatsapp()).isEqualTo("+5491111111111");
        assertThat(respuesta.fechaExpiracion()).hasToString("2099-01-01T00:00:00Z");
        assertThat(respuesta.toString()).doesNotContain("12345678", "+5491111111111");
        assertThat(new ValidarCredencialRequest("jwt-interno").toString()).doesNotContain("jwt-interno");
        verify(postRequestedFor(urlEqualTo(PATH)).withHeader("Content-Type", equalTo("application/json"))
                .withRequestBody(equalToJson("{\"credencial\":\"jwt-interno\"}")));
    }

    @Test
    @DisplayName("Spring crea el cliente con la URL y los tiempos configurados por propiedades")
    void configuraClienteDesdePropiedades(WireMockRuntimeInfo servidor) {
        stubFor(post(PATH).willReturn(aResponse().withHeader("Content-Type", "application/json")
                .withBody(RESPUESTA)));
        var contexto = new ApplicationContextRunner()
                .withInitializer(aplicacion -> aplicacion.getBeanFactory()
                        .setConversionService(ApplicationConversionService.getSharedInstance()))
                .withConfiguration(AutoConfigurations.of(RestClientAutoConfiguration.class))
                .withUserConfiguration(RestClientConfig.class)
                .withPropertyValues(
                        "clients.wa-credentials-service.base-url=" + servidor.getHttpBaseUrl(),
                        "clients.wa-credentials-service.connect-timeout=2s",
                        "clients.wa-credentials-service.read-timeout=3s");

        contexto.run(aplicacion -> {
            assertThat(aplicacion).hasNotFailed().hasSingleBean(CredencialesServiceClient.class);
            var respuesta = aplicacion.getBean(CredencialesServiceClient.class).validar("jwt-interno");

            assertThat(respuesta.vigente()).isTrue();
            assertThat(respuesta.documentoNumero()).isEqualTo("12345678");
        });
        verify(1, postRequestedFor(urlEqualTo(PATH)));
    }

    @Test
    @DisplayName("Consulta H2 en cada llamada y observa una revocación sin caché")
    void observaRevocacion() {
        stubFor(post(PATH).willReturn(aResponse().withHeader("Content-Type", "application/json")
                .withBody(RESPUESTA)));
        cliente.validar("jwt-interno");
        stubFor(post(PATH).willReturn(aResponse().withStatus(401).withBody("detalle-sensible")));

        assertThatThrownBy(() -> cliente.validar("jwt-interno"))
                .isInstanceOf(UnauthorizedException.class).hasMessageNotContaining("detalle-sensible");
        verify(2, postRequestedFor(urlEqualTo(PATH)));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" "})
    @DisplayName("Rechaza credenciales ausentes sin consultar H2")
    void rechazaCredencialAusente(String credencial) {
        assertThatThrownBy(() -> cliente.validar(credencial)).isInstanceOf(UnauthorizedException.class);
        verify(0, postRequestedFor(urlEqualTo(PATH)));
    }

    @ParameterizedTest
    @ValueSource(ints = {302, 400, 403, 404, 429, 500, 503})
    @DisplayName("Los errores de H2 impiden autorizar sin propagar el cuerpo remoto")
    void rechazaErrorRemoto(int estado) {
        stubFor(post(PATH).willReturn(aResponse().withStatus(estado).withBody("detalle-sensible")));

        assertThatThrownBy(() -> cliente.validar("jwt-interno"))
                .isInstanceOf(CredencialesServiceUnavailableException.class)
                .hasMessageNotContaining("detalle-sensible");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "no-json", "{}", "{\"vigente\":true}",
        "{\"vigente\":true,\"fechaExpiracion\":\"invalida\"}"})
    @DisplayName("Respuestas vacías, inválidas o incompletas nunca autorizan")
    void rechazaRespuestaIncompleta(String respuesta) {
        stubFor(post(PATH).willReturn(aResponse().withHeader("Content-Type", "application/json")
                .withBody(respuesta)));

        assertThatThrownBy(() -> cliente.validar("jwt-interno"))
                .isInstanceOfAny(CredencialesServiceUnavailableException.class, UnauthorizedException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"numeroWhatsapp", "documentoTipo", "documentoNumero"})
    @DisplayName("Una identidad vigente con datos obligatorios en blanco impide autorizar")
    void rechazaIdentidadEnBlanco(String campo) {
        String respuesta = RESPUESTA.replaceAll("\"" + campo + "\":\"[^\"]*\"", "\"" + campo + "\":\" \"");
        stubFor(post(PATH).willReturn(aResponse().withHeader("Content-Type", "application/json")
                .withBody(respuesta)));

        assertThatThrownBy(() -> cliente.validar("jwt-interno"))
                .isInstanceOf(CredencialesServiceUnavailableException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"numeroWhatsapp", "documentoTipo", "documentoNumero", "fechaExpiracion"})
    @DisplayName("Una identidad vigente con un dato obligatorio nulo impide autorizar")
    void rechazaIdentidadConDatoNulo(String campo) {
        String respuesta = RESPUESTA.replaceAll("\"" + campo + "\":\"[^\"]*\"", "\"" + campo + "\":null");
        stubFor(post(PATH).willReturn(aResponse().withHeader("Content-Type", "application/json")
                .withBody(respuesta)));

        assertThatThrownBy(() -> cliente.validar("jwt-interno"))
                .isInstanceOf(CredencialesServiceUnavailableException.class);
        verify(1, postRequestedFor(urlEqualTo(PATH)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"no-vigente", "expirada"})
    @DisplayName("Una sesión no vigente o vencida produce rechazo de autenticación")
    void rechazaSesionVencida(String caso) {
        String respuesta = "no-vigente".equals(caso) ? RESPUESTA.replace("true", "false")
                : RESPUESTA.replace("2099-01-01", "2000-01-01");
        stubFor(post(PATH).willReturn(aResponse().withHeader("Content-Type", "application/json")
                .withBody(respuesta)));

        assertThatThrownBy(() -> cliente.validar("jwt-interno")).isInstanceOf(UnauthorizedException.class);
    }

    @Test
    @DisplayName("El timeout de lectura configurado cancela la validación y conserva la causa")
    void limitaTiempoDeLectura(WireMockRuntimeInfo servidor) {
        var clienteConTimeoutCorto = crearCliente(servidor.getHttpBaseUrl(), Duration.ofMillis(200),
                Duration.ofMillis(200));
        stubFor(post(PATH).willReturn(aResponse().withFixedDelay(1000)
                .withHeader("Content-Type", "application/json").withBody(RESPUESTA)));

        assertThatThrownBy(() -> clienteConTimeoutCorto.validar("jwt-interno"))
                .isInstanceOf(CredencialesServiceUnavailableException.class)
                .hasRootCauseInstanceOf(java.net.SocketTimeoutException.class);
    }
}
