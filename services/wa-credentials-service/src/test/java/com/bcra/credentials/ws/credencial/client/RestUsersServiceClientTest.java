package com.bcra.credentials.ws.credencial.client;

import java.net.SocketTimeoutException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class RestUsersServiceClientTest {

    private MockRestServiceServer server;
    private UsersServiceClient client;

    @BeforeEach
    void configurar() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://h1.test");
        server = MockRestServiceServer.bindTo(builder).build();
        client = new RestUsersServiceClient(builder.build());
    }

    @ParameterizedTest
    @ValueSource(strings = {"ACTIVE", "SUSPENDED", "LOGICAL_DELETE"})
    @DisplayName("Consulta el estado de H1 e ignora los documentos de la respuesta")
    void consultaEstado(String estado) {
        server.expect(requestTo("http://h1.test/api/v1/users/DNI/12345678/status"))
                .andRespond(withSuccess("{\"estado\":\"" + estado + "\",\"documentoNumero\":\"12345678\"}",
                        MediaType.APPLICATION_JSON));

        assertThat(client.consultarEstado("DNI", "12345678").estado().name()).isEqualTo(estado);
        server.verify();
    }

    @Test
    @DisplayName("Distingue un cliente inexistente de una falla de H1")
    void informaClienteInexistente() {
        server.expect(requestTo("http://h1.test/api/v1/users/DNI/12345678/status"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThatThrownBy(() -> client.consultarEstado("DNI", "12345678"))
                .isInstanceOf(ClienteNoEncontradoException.class);
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "{}", "{\"estado\":\"DESCONOCIDO\"}", "no-json"})
    @DisplayName("Rechaza respuestas vacías o inválidas sin asumir estado activo")
    void rechazaRespuestaInvalida(String cuerpo) {
        server.expect(requestTo("http://h1.test/api/v1/users/DNI/12345678/status"))
                .andRespond(withSuccess(cuerpo, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.consultarEstado("DNI", "12345678"))
                .isInstanceOf(UsersServiceUnavailableException.class);
        server.verify();
    }

    @Test
    @DisplayName("Traduce errores de H1 sin propagar información sensible")
    void controlaErrorRemoto() {
        server.expect(requestTo("http://h1.test/api/v1/users/DNI/12345678/status"))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR).body("datos sensibles"));

        assertThatThrownBy(() -> client.consultarEstado("DNI", "12345678"))
                .isInstanceOf(UsersServiceUnavailableException.class)
                .hasMessage("No se pudo consultar el estado del cliente").hasNoCause();
        server.verify();
    }

    @Test
    @DisplayName("Controla el timeout sin reintentar ni autorizar por defecto")
    void controlaTimeout() {
        server.expect(requestTo("http://h1.test/api/v1/users/DNI/12345678/status"))
                .andRespond(withException(new SocketTimeoutException("timeout")));

        assertThatThrownBy(() -> client.consultarEstado("DNI", "12345678"))
                .isInstanceOf(UsersServiceUnavailableException.class);
        server.verify();
    }

    @Test
    @DisplayName("Rechaza documentos vacíos antes de llamar a H1")
    void rechazaDocumentoVacio() {
        assertThatThrownBy(() -> client.consultarEstado("", "12345678"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> client.consultarEstado("DNI", " "))
                .isInstanceOf(IllegalArgumentException.class);
        server.verify();
    }
}
