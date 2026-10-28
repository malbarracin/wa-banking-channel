package com.bcra.orchestrator.agent.client;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.client.reactive.JdkClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;

import com.bcra.orchestrator.agent.dto.TurnRequest;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.serverError;
import static com.github.tomakehurst.wiremock.client.WireMock.serviceUnavailable;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Cliente H9: contrato, resiliencia y trazabilidad sin datos sensibles")
class H9TurnClientTest {

    private static final String PATH = "/api/v1/agent/turn";
    private final TurnRequest request = new TurnRequest(UUID.randomUUID(), "conversation-test", "message-test",
            "whatsapp:+15005550006", "private customer text",
            new TurnRequest.Context(Map.of("state", "INITIAL"), List.of()));
    private WireMockServer server;
    private ListAppender<ILoggingEvent> logs;
    private Logger logger;

    @BeforeEach
    void startServer() {
        server = new WireMockServer(0);
        server.start();
        logs = new ListAppender<>();
        logs.start();
        logger = (Logger) LoggerFactory.getLogger(H9TurnClient.class);
        logger.addAppender(logs);
    }

    @AfterEach
    void stopServer() {
        server.stop();
        logger.detachAppender(logs);
        MDC.clear();
    }

    @Test
    void propagatesContractAndTracesWithoutSensitiveData() {
        server.stubFor(post(PATH).willReturn(okJson(responseBody())));
        MDC.put("correlationId", "previous");

        var response = client(10000, 2).turn(request);

        assertThat(response.reply().body()).isEqualTo("private reply");
        assertThat(MDC.get("correlationId")).isEqualTo("previous");
        server.verify(1, postRequestedFor(urlEqualTo(PATH))
                .withHeader("X-Correlation-Id", equalTo(request.correlationId().toString()))
                .withHeader("X-Conversation-Id", equalTo(request.conversationId()))
                .withHeader("X-Message-Id", equalTo(request.messageId()))
                .withHeader("X-Idempotency-Key", equalTo(request.messageId()))
                .withHeader("Authorization", equalTo("Bearer test-service-token"))
                .withHeader("Content-Type", containing("application/json"))
                .withRequestBody(matchingJsonPath("$.context.technicalContext.state", equalTo("INITIAL")))
                .withRequestBody(matchingJsonPath("$.customerAddress", equalTo(request.customerAddress())))
                .withRequestBody(matchingJsonPath("$.body", equalTo(request.body()))));
        assertThat(logs.list).hasSize(2).allSatisfy(event -> {
            assertThat(event.getMDCPropertyMap()).containsEntry("correlationId", request.correlationId().toString());
            assertThat(event.getFormattedMessage()).doesNotContain("private", "15005550006", "test-service-token");
            assertThat(event.getThrowableProxy()).isNull();
        });
        assertThat(request.toString() + request.context() + response + response.reply()).doesNotContain("private");
    }

    @Test
    void retriesTransientFailureWithSameIdempotencyKey() {
        server.stubFor(post(PATH).inScenario("retry").whenScenarioStateIs("Started")
                .willReturn(serverError()).willSetStateTo("recovered"));
        server.stubFor(post(PATH).inScenario("retry").whenScenarioStateIs("recovered")
                .willReturn(okJson(responseBody())));

        assertThat(client(10000, 2).turn(request).messageId()).isEqualTo(request.messageId());

        server.verify(2, postRequestedFor(urlEqualTo(PATH))
                .withHeader("X-Idempotency-Key", equalTo(request.messageId())));
        assertThat(MDC.get("correlationId")).isNull();
    }

    @ParameterizedTest
    @ValueSource(ints = {400, 401, 403, 409, 429, 302})
    void doesNotRetryIrrecoverableResponses(int status) {
        server.stubFor(post(PATH).willReturn(aResponse().withStatus(status).withBody("private error")));

        assertFailure(client(10000, 2), H9TurnException.Reason.REJECTED);

        server.verify(1, postRequestedFor(urlEqualTo(PATH)));
    }

    @Test
    void exhaustsServerErrorsWithBoundedAttempts() {
        server.stubFor(post(PATH).willReturn(serviceUnavailable()));

        assertFailure(client(10000, 2), H9TurnException.Reason.UNAVAILABLE);

        server.verify(3, postRequestedFor(urlEqualTo(PATH)));
    }

    @Test
    void timesOutAndExposesControlledFailureForContingency() {
        server.stubFor(post(PATH).willReturn(okJson(responseBody()).withFixedDelay(1500)));

        assertFailure(client(300, 1), H9TurnException.Reason.TIMEOUT);

        server.verify(2, postRequestedFor(urlEqualTo(PATH)));
        assertThat(logs.list.getLast().getFormattedMessage()).contains("TIMEOUT");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "{}", "not-json"})
    void rejectsEmptyOrMalformedResponsesWithoutRetry(String body) {
        server.stubFor(post(PATH).willReturn(okJson(body)));

        assertFailure(client(10000, 2), H9TurnException.Reason.INVALID_RESPONSE);

        server.verify(1, postRequestedFor(urlEqualTo(PATH)));
    }

    @Test
    void rejectsResponseForAnotherTurn() {
        server.stubFor(post(PATH).willReturn(okJson(responseBody().replace("message-test", "other-message"))));

        assertFailure(client(10000, 2), H9TurnException.Reason.INVALID_RESPONSE);
    }

    @Test
    void refusesToSendWithoutServiceCredential() {
        var client = new H9TurnClient(webClient(), "", Duration.ofSeconds(1), 0, Duration.ofMillis(1));

        assertFailure(client, H9TurnException.Reason.NOT_CONFIGURED);

        server.verify(0, postRequestedFor(urlEqualTo(PATH)));
    }

    @Test
    void mapsConnectionFailureWithoutLeakingTransportException() {
        var client = client(10000, 0);
        server.stop();

        assertFailure(client, H9TurnException.Reason.UNAVAILABLE);
    }

    @Test
    void rejectsInvalidRetryConfiguration() {
        assertThatThrownBy(() -> new H9TurnClient(webClient(), "token", Duration.ZERO, 0, Duration.ofMillis(1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private H9TurnClient client(long timeoutMillis, int retries) {
        return new H9TurnClient(webClient(), "test-service-token", Duration.ofMillis(timeoutMillis),
                retries, Duration.ofMillis(10));
    }

    private WebClient webClient() {
        return WebClient.builder().baseUrl(server.baseUrl())
                .clientConnector(new JdkClientHttpConnector()).build();
    }

    private void assertFailure(H9TurnClient client, H9TurnException.Reason reason) {
        assertThatThrownBy(() -> client.turn(request)).isInstanceOfSatisfying(H9TurnException.class, failure -> {
            assertThat(failure.getReason()).isEqualTo(reason);
            assertThat(failure.getCause()).isNull();
            assertThat(failure.getMessage()).doesNotContain("private", "token", "15005550006");
        });
    }

    private String responseBody() {
        return """
                {"correlationId":"%s","conversationId":"conversation-test","messageId":"message-test",
                 "reply":{"body":"private reply"},"escalate":false,"toolsUsed":[]}
                """.formatted(request.correlationId());
    }
}
