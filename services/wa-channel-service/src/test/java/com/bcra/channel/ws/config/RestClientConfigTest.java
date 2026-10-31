package com.bcra.channel.ws.config;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.web.client.RestClientAutoConfiguration;
import org.springframework.boot.convert.ApplicationConversionService;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import com.bcra.channel.ws.vinculo.client.CredencialesServiceClient;
import com.bcra.channel.ws.vinculo.client.UsersServiceClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Configuración HTTP de los clientes externos")
class RestClientConfigTest {

    @Test
    @DisplayName("Envía a H2 el secreto de servicio de H3 al revocar")
    void enviaAutenticacionDeServicio() throws IOException {
        var recibido = new java.util.concurrent.atomic.AtomicReference<String>();
        HttpServer servidor = HttpServer.create(new java.net.InetSocketAddress("127.0.0.1", 0), 0);
        servidor.createContext("/", intercambio -> {
            recibido.set(intercambio.getRequestHeaders().getFirst("Authorization"));
            intercambio.sendResponseHeaders(204, -1);
            intercambio.close();
        });
        servidor.start();
        try {
            var cliente = new RestClientConfig("h3-test-token").credencialesServiceClient(RestClient.builder(),
                    "http://127.0.0.1:" + servidor.getAddress().getPort(), Duration.ofSeconds(1),
                    Duration.ofSeconds(1));
            assertThat(cliente.solicitarRevocacion("+5491123456789", "BLOQUEO")).isTrue();
            assertThat(recibido.get()).isEqualTo("Bearer h3-test-token");
        } finally {
            servidor.stop(0);
        }
    }

    @Test
    @DisplayName("Crea ambos adaptadores con URLs y duraciones configuradas")
    void cargaProperties() {
        new ApplicationContextRunner()
                .withInitializer(context -> context.getBeanFactory()
                        .setConversionService(ApplicationConversionService.getSharedInstance()))
                .withConfiguration(AutoConfigurations.of(RestClientAutoConfiguration.class))
                .withUserConfiguration(RestClientConfig.class)
                .withPropertyValues("clients.wa-users-service.base-url=http://h1.test",
                        "clients.credenciales-service.base-url=http://h2.test",
                        "clients.connect-timeout=150ms", "clients.read-timeout=250ms")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(UsersServiceClient.class);
                    assertThat(context).hasSingleBean(CredencialesServiceClient.class);
                });
    }

    @Test
    @DisplayName("Rechaza timeouts cero o negativos para evitar esperas ilimitadas")
    void rechazaTimeoutsInvalidos() {
        var configuracion = new RestClientConfig("h3-test-token");

        assertThatThrownBy(() -> configuracion.usersServiceClient(RestClient.builder(), "http://h1.test",
                Duration.ZERO, Duration.ofSeconds(1))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> configuracion.usersServiceClient(RestClient.builder(), "http://h1.test",
                Duration.ofSeconds(-1), Duration.ofSeconds(1))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> configuracion.usersServiceClient(RestClient.builder(), "http://h1.test",
                Duration.ofSeconds(1), Duration.ZERO)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> configuracion.usersServiceClient(RestClient.builder(), "http://h1.test",
                Duration.ofSeconds(1), Duration.ofSeconds(-1))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Aplica timeout de lectura real y H2 mantiene su contrato best-effort")
    void limitaLecturaReal() throws IOException {
        HttpServer servidor = HttpServer.create(new java.net.InetSocketAddress("127.0.0.1", 0), 0);
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        var liberarRespuestas = new CountDownLatch(1);
        var solicitudes = new CountDownLatch(2);
        servidor.setExecutor(executor);
        servidor.createContext("/", intercambio -> {
            solicitudes.countDown();
            try {
                liberarRespuestas.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            } finally {
                intercambio.close();
            }
        });
        servidor.start();
        try {
            String baseUrl = "http://127.0.0.1:" + servidor.getAddress().getPort();
            var configuracion = new RestClientConfig("h3-test-token");
            UsersServiceClient usuarios = configuracion.usersServiceClient(RestClient.builder(), baseUrl,
                    Duration.ofMillis(500), Duration.ofMillis(100));
            CredencialesServiceClient credenciales = configuracion.credencialesServiceClient(RestClient.builder(),
                    baseUrl, Duration.ofMillis(500), Duration.ofMillis(100));

            assertThatThrownBy(() -> usuarios.consultarEstado("DNI", "30111222"))
                    .isInstanceOf(ResourceAccessException.class)
                    .hasRootCauseInstanceOf(java.net.SocketTimeoutException.class);
            assertThat(credenciales.solicitarRevocacion("5491112345678", "SOLICITUD_CLIENTE")).isFalse();
            assertThat(solicitudes.getCount()).isZero();
        } finally {
            liberarRespuestas.countDown();
            servidor.stop(0);
            executor.shutdownNow();
        }
    }
}
