package com.bcra.gateway.outbound;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.slf4j.MDC;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;

import com.bcra.gateway.messaging.producer.StatusEventPublisher;
import com.bcra.gateway.normalization.StatusNormalizer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.twilio.http.HttpClient;
import com.twilio.http.Response;
import com.twilio.http.TwilioRestClient;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class OutboundFlowTest {
    private static final String SID = "SM00000000000000000000000000000000";
    private final ObjectMapper mapper = new ObjectMapper();
    private OutboundSchemas schemas;
    private OutboundStore store;
    private JdbcTemplate jdbc;
    private HttpClient http;
    private KafkaTemplate<String, String> kafka;
    private Acknowledgment acknowledgment;
    private OutboundEventConsumer consumer;
    private SimpleMeterRegistry metrics;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() throws Exception {
        schemas = new OutboundSchemas(mapper);
        jdbc = new JdbcTemplate(new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID()
                + ";DB_CLOSE_DELAY=-1", "sa", ""));
        store = new OutboundStore(jdbc);
        http = mock(HttpClient.class);
        kafka = mock(KafkaTemplate.class);
        acknowledgment = mock(Acknowledgment.class);
        metrics = new SimpleMeterRegistry();
        when(http.reliableRequest(any())).thenReturn(new Response(
                "{\"sid\":\"" + SID + "\",\"status\":\"queued\"}", 201));
        when(kafka.send(any(ProducerRecord.class))).thenReturn(CompletableFuture.completedFuture(null));
        consumer = createConsumer(store);
    }

    private OutboundEventConsumer createConsumer(OutboundStore repository) throws Exception {
        var client = new TwilioRestClient.Builder("AC00000000000000000000000000000000", "synthetic")
                .httpClient(http).build();
        var sender = new TwilioSender(client, "whatsapp:+15005550006", URI.create("https://bank.example/status"));
        return new OutboundEventConsumer(schemas, repository, sender, new StatusEventPublisher(kafka, mapper),
                mapper, metrics);
    }

    ObjectNode event(String messageId) {
        var event = mapper.createObjectNode();
        event.put("eventId", UUID.randomUUID().toString());
        event.put("eventType", "MessageReadyToSend");
        event.put("eventVersion", 1);
        event.put("occurredAt", "2026-01-01T00:00:00Z");
        event.put("correlationId", UUID.randomUUID().toString());
        event.put("conversationId", "conversation-1");
        event.put("messageId", messageId);
        event.putObject("payload").put("customerAddress", "whatsapp:+15005550007").put("body", "Hola");
        return event;
    }

    ConsumerRecord<String, String> record(ObjectNode event) {
        var record = new ConsumerRecord<String, String>(OutboundSchemas.READY, 0, 1,
                event.path("conversationId").asText(), event.toString());
        for (String header : new String[] {"correlationId", "eventType", "eventVersion"}) {
            record.headers().add(header, event.path(header).asText().getBytes(StandardCharsets.UTF_8));
        }
        return record;
    }

    @Test
    @DisplayName("Envía una sola vez tras reentrega y reapertura del repositorio")
    void sendsOnceAcrossRedeliveryAndRepositoryReopen() throws Exception {
        var event = event("message-1");
        MDC.put("correlationId", "previous");
        consumer.consume(record(event), acknowledgment);
        assertThat(MDC.get("correlationId")).isEqualTo("previous");
        MDC.clear();

        createConsumer(new OutboundStore(jdbc)).consume(record(event), acknowledgment);

        verify(http).reliableRequest(any());
        verify(acknowledgment, times(2)).acknowledge();
        var saved = store.find("message-1").orElseThrow();
        assertThat(saved.sid()).isEqualTo(SID);
        assertThat(saved.status()).isEqualTo("queued");
        assertThat(saved.state()).isEqualTo("SENT");
        assertThat(metrics.counter("gateway.outbound.sent").count()).isEqualTo(1);
    }

    @Test
    @SuppressWarnings("unchecked")
    @DisplayName("Publica el estado con el ID de negocio y correlación original")
    void publishesStatusWithBusinessIdentity() throws Exception {
        var event = event("business-id");
        consumer.consume(record(event), acknowledgment);

        ArgumentCaptor<ProducerRecord<String, String>> sent = ArgumentCaptor.forClass(ProducerRecord.class);
        verify(kafka).send(sent.capture());
        var status = mapper.readTree(sent.getValue().value());
        assertThat(status.path("messageId").asText()).isEqualTo("business-id");
        assertThat(status.path("correlationId")).isEqualTo(event.path("correlationId"));
        assertThat(sent.getValue().value()).doesNotContain("Hola", "whatsapp:", "synthetic");
        assertThat(sent.getValue().key()).isEqualTo("conversation-1");
    }

    @Test
    @DisplayName("Rechaza cambios de contenido con el mismo messageId")
    void rejectsConflictingDuplicate() {
        var event = event("conflict");
        consumer.consume(record(event), acknowledgment);
        ((ObjectNode) event.path("payload")).put("body", "Otro contenido");

        assertThatThrownBy(() -> consumer.consume(record(event), acknowledgment))
                .isInstanceOf(OutboundException.class).hasMessage("MESSAGE_ID_CONFLICT");
        verify(http).reliableRequest(any());
    }

    @Test
    @DisplayName("Un envío reservado antes de una caída no se repite")
    void doesNotResendAmbiguousReservation() {
        store.claim("reserved", "hash", "conversation", UUID.randomUUID().toString());

        assertThatThrownBy(() -> store.claim("reserved", "hash", "conversation", UUID.randomUUID().toString()))
                .isInstanceOf(OutboundException.class).hasMessage("SEND_REQUIRES_RECONCILIATION");
        verifyNoInteractions(http);
    }

    @ParameterizedTest
    @ValueSource(ints = {429, 500, 503})
    @DisplayName("Un error transitorio permite reintentar hasta un envío exitoso")
    void retriesTransientErrors(int status) {
        when(http.reliableRequest(any())).thenReturn(new Response(
                "{\"status\":" + status + ",\"code\":20429,\"message\":\"private\"}", status))
                .thenReturn(new Response("{\"sid\":\"" + SID + "\",\"status\":\"sent\"}", 201));
        var event = event("retry");

        assertThatThrownBy(() -> consumer.consume(record(event), acknowledgment))
                .isInstanceOf(OutboundException.class).hasMessage("TWILIO_20429");
        assertThat(store.find("retry").orElseThrow().state()).isEqualTo("RETRYABLE");
        verifyNoInteractions(acknowledgment);
        consumer.consume(record(event), acknowledgment);

        verify(http, times(2)).reliableRequest(any());
        verify(acknowledgment).acknowledge();
    }

    @Test
    @DisplayName("El presupuesto durable limita los intentos incluso tras recrear el consumidor")
    void boundsDurableAttempts() {
        var failure = new OutboundException(OutboundException.Kind.TRANSIENT, "TWILIO_20429");
        for (int attempt = 0; attempt < 4; attempt++) {
            store.claim("budget", "hash", "conversation", UUID.randomUUID().toString());
            store.failed("budget", failure);
        }
        assertThatThrownBy(() -> store.claim("budget", "hash", "conversation", UUID.randomUUID().toString()))
                .hasMessage("SEND_REQUIRES_RECONCILIATION");
        assertThat(store.find("budget").orElseThrow().attempts()).isEqualTo(4);
    }

    @Test
    @DisplayName("El fallo al publicar estado no repite el POST exitoso")
    @SuppressWarnings("unchecked")
    void statusFailureDoesNotRepeatSend() {
        when(kafka.send(any(ProducerRecord.class))).thenReturn(CompletableFuture.failedFuture(
                new IllegalStateException("Unavailable"))).thenReturn(CompletableFuture.completedFuture(null));
        var event = event("status-retry");

        assertThatThrownBy(() -> consumer.consume(record(event), acknowledgment))
                .hasMessage("STATUS_PUBLICATION_FAILED");
        consumer.consume(record(event), acknowledgment);

        verify(http).reliableRequest(any());
        verify(acknowledgment).acknowledge();
    }

    @Test
    @DisplayName("El timeout ambiguo nunca genera un segundo POST")
    void doesNotRetryAmbiguousTimeout() {
        doThrow(new com.twilio.exception.ApiConnectionException("sensitive"))
                .when(http).reliableRequest(any());
        var event = event("timeout");

        assertThatThrownBy(() -> consumer.consume(record(event), acknowledgment))
                .hasMessage("TWILIO_CONNECTION_AMBIGUOUS");
        assertThatThrownBy(() -> consumer.consume(record(event), acknowledgment))
                .hasMessage("SEND_REQUIRES_RECONCILIATION");
        verify(http).reliableRequest(any());
        verifyNoInteractions(acknowledgment);
    }

    @Test
    @DisplayName("El error permanente queda registrado sin confirmar ni repetir envío")
    void recordsPermanentFailure() {
        when(http.reliableRequest(any())).thenReturn(new Response(
                "{\"status\":400,\"code\":21614,\"message\":\"private\"}", 400));

        assertThatThrownBy(() -> consumer.consume(record(event("permanent")), acknowledgment))
                .hasMessage("TWILIO_21614");
        assertThat(store.find("permanent").orElseThrow().state()).isEqualTo("FAILED");
        verifyNoInteractions(acknowledgment, kafka);
    }

    @Test
    @DisplayName("No envía eventos inválidos, clave incorrecta ni headers inconsistentes")
    void rejectsInvalidContractAndTransport() {
        var invalid = event("invalid");
        invalid.put("extra", true);
        assertThatThrownBy(() -> consumer.consume(record(invalid), acknowledgment))
                .hasMessage("SCHEMA_VALIDATION_FAILED");
        assertThatThrownBy(() -> schemas.validate(OutboundSchemas.READY, "not-json"))
                .hasMessage("SCHEMA_VALIDATION_FAILED");
        assertThatThrownBy(() -> schemas.validate(OutboundSchemas.READY, "null"))
                .hasMessage("SCHEMA_VALIDATION_FAILED");
        var noHeaders = new ConsumerRecord<String, String>(OutboundSchemas.READY, 0, 1,
                "conversation-1", event("headers").toString());
        assertThatThrownBy(() -> consumer.consume(noHeaders, acknowledgment)).hasMessage("INVALID_EVENT_HEADERS");
        var wrongKey = new ConsumerRecord<String, String>(OutboundSchemas.READY, 0, 1,
                "wrong-key", event("key").toString());
        assertThatThrownBy(() -> consumer.consume(wrongKey, acknowledgment)).hasMessage("INVALID_PARTITION_KEY");
        verifyNoInteractions(http, kafka, acknowledgment);
    }

    @ParameterizedTest
    @ValueSource(strings = {"correlationId", "eventType", "eventVersion"})
    @DisplayName("Rechaza cada header inconsistente antes de reservar o enviar")
    void rejectsMismatchedTransportHeader(String header) {
        var event = event("mismatched-header");
        var record = record(event);
        record.headers().remove(header);
        record.headers().add(header, "incorrect".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> consumer.consume(record, acknowledgment))
                .hasMessage("INVALID_EVENT_HEADERS");

        assertThat(store.find("mismatched-header")).isEmpty();
        verifyNoInteractions(http, kafka, acknowledgment);
    }

    @Test
    @DisplayName("Un callback recupera la identidad y correlación guardadas por outbound")
    void callbackResolvesOriginalIdentity() {
        var event = event("business-message");
        consumer.consume(record(event), acknowledgment);
        var beans = new StaticListableBeanFactory(Map.of("outboundStore", store));
        var normalizer = new StatusNormalizer(mapper, beans.getBeanProvider(OutboundStore.class));

        var status = normalizer.normalize(Map.of("MessageSid", SID, "MessageStatus", "delivered",
                "From", "whatsapp:+15005550006", "To", "whatsapp:+15005550007"), UUID.randomUUID().toString());

        assertThat(status.path("messageId")).isEqualTo(event.path("messageId"));
        assertThat(status.path("correlationId")).isEqualTo(event.path("correlationId"));
        assertThat(status.path("conversationId")).isEqualTo(event.path("conversationId"));
    }
}
