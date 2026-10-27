package com.bcra.accounts.ws.config;

import java.net.InetSocketAddress;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import com.bcra.accounts.ws.cuenta.client.CredencialesServiceUnavailableException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RestClientConfigTest {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(2);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(5);
    private static final String VALIDAR_PATH = "/api/v1/credenciales/validar";

    @Test
    @DisplayName("El cliente de credenciales envía la validación a la URL configurada")
    void enviaValidacionAlServidorConfigurado() throws Exception {
        HttpServer servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        servidor.createContext(VALIDAR_PATH, intercambio -> {
            try (intercambio) {
                intercambio.getRequestBody().readAllBytes();
                int estado = "POST".equals(intercambio.getRequestMethod()) ? 204 : 405;
                intercambio.sendResponseHeaders(estado, -1);
            }
        });
        servidor.start();
        try {
            RestClient cliente = creaCliente(servidor);

            var respuesta = cliente.post().uri(VALIDAR_PATH).body("credencial-de-prueba")
                    .retrieve().toBodilessEntity();

            assertThat(respuesta.getStatusCode().value()).isEqualTo(204);
        } finally {
            servidor.stop(0);
        }
    }

    @Test
    @DisplayName("El cliente conserva el rechazo HTTP del servicio de credenciales")
    void propagaRechazoDeCredenciales() throws Exception {
        HttpServer servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        servidor.createContext(VALIDAR_PATH, intercambio -> {
            try (intercambio) {
                intercambio.getRequestBody().readAllBytes();
                intercambio.sendResponseHeaders(401, -1);
            }
        });
        servidor.start();
        try {
            RestClient cliente = creaCliente(servidor);

            assertThatThrownBy(() -> cliente.post().uri(VALIDAR_PATH).body("credencial-de-prueba")
                    .retrieve().toBodilessEntity())
                    .isInstanceOfSatisfying(RestClientResponseException.class,
                            excepcion -> assertThat(excepcion.getStatusCode().value()).isEqualTo(401));
        } finally {
            servidor.stop(0);
        }
    }

    @Test
    @DisplayName("El timeout de lectura configurado impide esperar indefinidamente a H2")
    void aplicaTimeoutDeLecturaAlValidar() throws Exception {
        var liberarServidor = new CountDownLatch(1);
        HttpServer servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        servidor.createContext(VALIDAR_PATH, intercambio -> {
            try (intercambio) {
                intercambio.getRequestBody().readAllBytes();
                try {
                    liberarServidor.await(5, TimeUnit.SECONDS);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                }
            }
        });
        servidor.start();
        try {
            var configuracion = new RestClientConfig();
            String baseUrl = "http://127.0.0.1:" + servidor.getAddress().getPort();
            RestClient transporte = configuracion.credencialesRestClient(RestClient.builder(), baseUrl,
                    CONNECT_TIMEOUT, Duration.ofMillis(100));
            var cliente = configuracion.credencialesServiceClient(transporte);

            assertThatThrownBy(() -> cliente.validar("jwt-de-prueba"))
                    .isInstanceOf(CredencialesServiceUnavailableException.class)
                    .hasRootCauseInstanceOf(SocketTimeoutException.class);
        } finally {
            liberarServidor.countDown();
            servidor.stop(0);
        }
    }

    private RestClient creaCliente(HttpServer servidor) {
        String baseUrl = "http://127.0.0.1:" + servidor.getAddress().getPort();
        return new RestClientConfig().credencialesRestClient(RestClient.builder(), baseUrl,
                CONNECT_TIMEOUT, READ_TIMEOUT);
    }
}
