package com.bcra.gateway.outbound;

import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OutboundRecoveryTest {
    @TempDir
    Path directory;

    @Test
    @DisplayName("La reserva y resultado sobreviven al cierre real de la base de datos")
    void persistsAcrossDatabaseShutdown() {
        String url = "jdbc:h2:file:" + directory.resolve("outbound").toAbsolutePath().toString().replace('\\', '/')
                + ";WRITE_DELAY=0";
        var jdbc = new JdbcTemplate(new DriverManagerDataSource(url, "sa", ""));
        var store = new OutboundStore(jdbc);
        String correlation = UUID.randomUUID().toString();
        store.claim("persistent", "hash", "conversation", correlation);
        store.sent("persistent", "SM00000000000000000000000000000000", "queued");
        jdbc.execute("SHUTDOWN");

        var reopened = new OutboundStore(new JdbcTemplate(new DriverManagerDataSource(url, "sa", "")));

        assertThat(reopened.claim("persistent", "hash", "conversation", correlation).state()).isEqualTo("SENT");
        assertThat(reopened.findBySid("SM00000000000000000000000000000000").orElseThrow().messageId())
                .isEqualTo("persistent");
    }

    @Test
    @SuppressWarnings("unchecked")
    @DisplayName("Una recuperación publica DLQ válida sin headers de excepción ni datos del proveedor")
    void publishesSafeContractDlqAndPropagatesBrokerFailure() throws Exception {
        org.springframework.kafka.core.KafkaTemplate<String, String> kafka = mock(
                org.springframework.kafka.core.KafkaTemplate.class);
        var mapper = new ObjectMapper();
        var schemas = new OutboundSchemas(mapper);
        var publisher = new OutboundDeadLetterPublisher(kafka, mapper, schemas, new SimpleMeterRegistry());
        when(kafka.send(any(ProducerRecord.class))).thenReturn(CompletableFuture.completedFuture(null));
        var record = new ConsumerRecord<String, String>(OutboundSchemas.READY, 2, 10, "conversation", "invalid-json");
        publisher.accept(record, new OutboundException(OutboundException.Kind.INVALID, "SCHEMA_VALIDATION_FAILED"));

        ArgumentCaptor<ProducerRecord<String, String>> sent = ArgumentCaptor.forClass(ProducerRecord.class);
        verify(kafka).send(sent.capture());
        var event = schemas.validate(OutboundSchemas.DLQ, sent.getValue().value());
        assertThat(event.path("payload").path("originalOffset").asInt()).isEqualTo(10);
        assertThat(event.path("payload").path("errorCode").asText()).isEqualTo("SCHEMA_VALIDATION_FAILED");
        assertThat(sent.getValue().headers().lastHeader("kafka_dlt-exception-stacktrace")).isNull();

        when(kafka.send(any(ProducerRecord.class))).thenReturn(CompletableFuture.failedFuture(
                new IllegalStateException("Broker unavailable")));
        assertThatThrownBy(() -> publisher.accept(record,
                new OutboundException(OutboundException.Kind.PERMANENT, "TWILIO_21614")))
                .hasMessage("DLQ publication failed");
    }
}
