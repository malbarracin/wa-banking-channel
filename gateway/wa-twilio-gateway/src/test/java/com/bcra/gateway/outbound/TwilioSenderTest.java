package com.bcra.gateway.outbound;

import java.net.URI;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.twilio.http.Request;
import com.twilio.http.Response;
import com.twilio.http.TwilioRestClient;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TwilioSenderTest {
    private static final String PATH = "/2010-04-01/Accounts/AC00000000000000000000000000000000/Messages.json";
    private WireMockServer server;
    private TwilioSender sender;
    private TwilioRestClient client;

    @BeforeEach
    void startServer() {
        server = new WireMockServer(0);
        server.start();
        // Keep the production SDK and one-attempt HTTP stack; redirect only its host to WireMock.
        var transport = new SingleAttemptHttpClient() {
            @Override
            public Response makeRequest(Request original) {
                var redirected = new Request(original.getMethod(), server.baseUrl() + PATH);
                redirected.setAuth(original.getUsername(), original.getPassword());
                original.getPostParams().forEach((key, values) -> values.forEach(value ->
                        redirected.addPostParam(key, value)));
                return super.makeRequest(redirected);
            }
        };
        client = new TwilioRestClient.Builder("AC00000000000000000000000000000000", "synthetic-token")
                .httpClient(transport).build();
        sender = new TwilioSender(client, "whatsapp:+15005550006", URI.create("https://bank.example/status"));
    }

    @AfterEach
    void stopServer() {
        server.stop();
    }

    @Test
    @DisplayName("El SDK envía destinatario, emisor, cuerpo y callback correctos")
    void sendsExpectedTwilioRequest() {
        server.stubFor(post(PATH).willReturn(aResponse().withStatus(201).withHeader("Content-Type", "application/json")
                .withBody("{\"sid\":\"SM00000000000000000000000000000000\",\"status\":\"queued\"}")));

        var receipt = sender.send("whatsapp:+15005550007", "Hola mundo");

        assertThat(receipt.sid()).isEqualTo("SM00000000000000000000000000000000");
        server.verify(1, postRequestedFor(urlEqualTo(PATH))
                .withFormParam("To", equalTo("whatsapp:+15005550007"))
                .withFormParam("From", equalTo("whatsapp:+15005550006"))
                .withFormParam("Body", equalTo("Hola mundo"))
                .withFormParam("StatusCallback", equalTo("https://bank.example/status"))
                .withHeader("Authorization", containing("Basic ")));
    }

    @ParameterizedTest
    @ValueSource(ints = {400, 401, 429, 500, 503})
    @DisplayName("El SDK hace un solo POST por intento y expone únicamente el código seguro")
    void delegatesRetryPolicyToKafka(int status) {
        server.stubFor(post(PATH).willReturn(aResponse().withStatus(status)
                .withBody("{\"status\":" + status + ",\"code\":21614,\"message\":\"secret phone\"}")));

        assertThatThrownBy(() -> sender.send("whatsapp:+15005550007", "Hola"))
                .isInstanceOf(OutboundException.class).hasMessage("TWILIO_21614")
                .satisfies(failure -> assertThat(((OutboundException) failure).kind()).isEqualTo(
                        status == 429 || status >= 500 ? OutboundException.Kind.TRANSIENT
                                : OutboundException.Kind.PERMANENT));
        server.verify(1, postRequestedFor(urlEqualTo(PATH)));
    }

    @Test
    @DisplayName("El timeout de transporte es ambiguo y no se reintenta dentro del SDK")
    void timesOutWithoutAutomaticResend() {
        server.stubFor(post(PATH).willReturn(aResponse().withFixedDelay(5500).withStatus(201)));

        assertThatThrownBy(() -> sender.send("whatsapp:+15005550007", "Hola"))
                .hasMessage("TWILIO_CONNECTION_AMBIGUOUS");
        server.verify(1, postRequestedFor(urlEqualTo(PATH)));
    }

    @Test
    @DisplayName("Rechaza configuración insegura y destinos que no son WhatsApp E164")
    void validatesConfigurationAndDestination() {
        assertThatThrownBy(() -> new TwilioSender(client, "invalid", URI.create("https://bank.example/status")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TwilioSender(client, "whatsapp:+15005550006", URI.create("http://bank/status")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> sender.send("not-a-phone", "Hola")).hasMessage("INVALID_DESTINATION");
    }
}
