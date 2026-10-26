package com.bcra.channel.ws.vinculo.client;

import java.net.SocketTimeoutException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.client.RestClientTest;
import org.springframework.boot.test.web.client.MockServerRestClientCustomizer;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

@RestClientTest
@ExtendWith(OutputCaptureExtension.class)
@DisplayName("Clientes externos de H1 y H2")
class ExternalClientsTest {

    private static final String BASE_URL = "http://externo.test";
    private static final String ESTADO = BASE_URL + "/api/v1/users/DNI/30111222/status";
    private static final String PERFIL = BASE_URL + "/api/v1/users/DNI/30111222";
    private static final String REVOCACION = BASE_URL + "/api/v1/credenciales/5491112345678/revocacion";

    @Autowired
    private RestClient.Builder builder;

    @Autowired
    private MockServerRestClientCustomizer customizer;

    private MockRestServiceServer server;

    private UsersServiceClient usuarios;
    private CredencialesServiceClient credenciales;

    @BeforeEach
    void prepararClientes() {
        RestClient cliente = builder.baseUrl(BASE_URL).build();
        server = customizer.getServer(builder);
        usuarios = new RestUsersServiceClient(cliente);
        credenciales = new RestCredencialesServiceClient(cliente);
    }

    @ParameterizedTest
    @ValueSource(strings = {"ACTIVE", "SUSPENDED", "LOGICAL_DELETE"})
    @DisplayName("Lee los estados reales publicados por H1")
    void consultaEstado(String estado) {
        server.expect(requestTo(ESTADO)).andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        {"documento":{"tipo":"DNI","numero":"30111222"},"estado":"%s"}
                        """.formatted(estado), MediaType.APPLICATION_JSON));

        assertThat(usuarios.consultarEstado("DNI", "30111222").estado().name()).isEqualTo(estado);
        server.verify();
    }

    @Test
    @DisplayName("Deserializa solamente los campos permitidos del perfil")
    void consultaPerfil() {
        server.expect(requestTo(PERFIL)).andExpect(method(HttpMethod.GET)).andRespond(withSuccess("""
                {"id":"123","documento":{"tipo":"DNI","numero":"30111222"},
                 "fechaNacimiento":"1990-05-15","nombre":"Ana","apellido":"Pérez",
                 "nombreTratamiento":"Anita","email":"ana@example.test","telefono":"5551234"}
                """, MediaType.APPLICATION_JSON));

        assertThat(usuarios.consultarPerfil("DNI", "30111222"))
                .isEqualTo(new ClientePerfilResponse("Ana", "Pérez", "Anita", "ana@example.test", "5551234"));
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(ints = {404, 500})
    @DisplayName("Propaga errores de H1 sin convertirlos en una consulta exitosa")
    void propagaErroresDeUsuarios(int status) {
        server.expect(requestTo(ESTADO)).andRespond(withStatus(HttpStatus.valueOf(status)));
        server.expect(requestTo(PERFIL)).andRespond(withStatus(HttpStatus.valueOf(status)));

        assertThatThrownBy(() -> usuarios.consultarEstado("DNI", "30111222"))
                .isInstanceOf(RestClientResponseException.class);
        assertThatThrownBy(() -> usuarios.consultarPerfil("DNI", "30111222"))
                .isInstanceOf(RestClientResponseException.class);
        server.verify();
    }

    @Test
    @DisplayName("Propaga timeout de ambas consultas H1")
    void propagaTimeoutDeUsuarios() {
        server.expect(requestTo(ESTADO)).andRespond(withException(new SocketTimeoutException()));
        server.expect(requestTo(PERFIL)).andRespond(withException(new SocketTimeoutException()));

        assertThatThrownBy(() -> usuarios.consultarEstado("DNI", "30111222"))
                .isInstanceOf(ResourceAccessException.class);
        assertThatThrownBy(() -> usuarios.consultarPerfil("DNI", "30111222"))
                .isInstanceOf(ResourceAccessException.class);
        server.verify();
    }

    @Test
    @DisplayName("Rechaza cuerpos vacíos y estados ausentes de H1")
    void rechazaRespuestaVacia() {
        server.expect(requestTo(ESTADO)).andRespond(withSuccess());
        server.expect(requestTo(ESTADO)).andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(PERFIL)).andRespond(withSuccess());

        assertThatThrownBy(() -> usuarios.consultarEstado("DNI", "30111222"))
                .isInstanceOf(RestClientException.class);
        assertThatThrownBy(() -> usuarios.consultarEstado("DNI", "30111222"))
                .isInstanceOf(RestClientException.class);
        assertThatThrownBy(() -> usuarios.consultarPerfil("DNI", "30111222"))
                .isInstanceOf(RestClientException.class);
        server.verify();
    }

    @Test
    @DisplayName("Solicita revocación con el motivo y sin reintentos")
    void solicitaRevocacion() {
        server.expect(requestTo(REVOCACION)).andExpect(method(HttpMethod.POST))
                .andExpect(content().json("{\"motivo\":\"SOLICITUD_CLIENTE\"}"))
                .andRespond(withStatus(HttpStatus.NO_CONTENT));

        assertThatCode(() -> credenciales.solicitarRevocacion("5491112345678", "SOLICITUD_CLIENTE"))
                .doesNotThrowAnyException();
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(ints = {400, 503})
    @DisplayName("Registra errores H2 sin propagarlos ni filtrar datos")
    void toleraErroresDeCredenciales(int status, CapturedOutput output) {
        server.expect(requestTo(REVOCACION)).andRespond(withStatus(HttpStatus.valueOf(status))
                .body("secreto-del-servidor"));

        assertThatCode(() -> credenciales.solicitarRevocacion("5491112345678", "motivo-privado"))
                .doesNotThrowAnyException();
        assertThat(output.getOut()).contains("No se confirmó la solicitud de revocación a H2")
                .doesNotContain("5491112345678", "motivo-privado", "secreto-del-servidor");
        server.verify();
    }

    @Test
    @DisplayName("Registra timeout H2 y permite continuar la operación local")
    void toleraTimeoutDeCredenciales(CapturedOutput output) {
        server.expect(requestTo(REVOCACION)).andRespond(withException(new SocketTimeoutException()));

        assertThatCode(() -> credenciales.solicitarRevocacion("5491112345678", "SOLICITUD_CLIENTE"))
                .doesNotThrowAnyException();
        assertThat(output.getOut()).contains("ResourceAccessException");
        server.verify();
    }
}
