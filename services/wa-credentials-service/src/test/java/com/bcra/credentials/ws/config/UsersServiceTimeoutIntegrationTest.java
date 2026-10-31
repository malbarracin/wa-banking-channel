package com.bcra.credentials.ws.config;

import java.net.InetAddress;
import java.net.ServerSocket;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import com.bcra.credentials.ws.credencial.client.UsersServiceUnavailableException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

class UsersServiceTimeoutIntegrationTest {

    private static final Duration READ_TIMEOUT = Duration.ofMillis(100);
    private static final Duration TEST_TIMEOUT = Duration.ofSeconds(5);

    @Test
    @DisplayName("Aplica el timeout de lectura configurado ante un H1 que acepta pero no responde")
    void controlaTimeoutRealDeLectura() throws Exception {
        var liberarServidor = new CountDownLatch(1);
        var conexionAceptada = new CountDownLatch(1);
        try (var servidor = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"));
                var executor = Executors.newSingleThreadExecutor()) {
            var respuesta = executor.submit(() -> {
                try (var conexion = servidor.accept()) {
                    conexionAceptada.countDown();
                    liberarServidor.await(TEST_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
                }
                return true;
            });
            var cliente = new RestClientConfig().usersServiceClient(RestClient.builder(),
                    "http://127.0.0.1:" + servidor.getLocalPort(), Duration.ofSeconds(1), READ_TIMEOUT);

            try {
                assertTimeoutPreemptively(TEST_TIMEOUT, () ->
                        assertThatThrownBy(() -> cliente.consultarEstado("DNI", "12345678"))
                                .isInstanceOf(UsersServiceUnavailableException.class)
                                .hasMessage("No se pudo consultar el estado del cliente")
                                .hasNoCause());
                assertThat(conexionAceptada.await(TEST_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)).isTrue();
            } finally {
                liberarServidor.countDown();
                servidor.close();
            }
            assertThat(respuesta.get(TEST_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)).isTrue();
        }
    }
}
