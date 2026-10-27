package com.bcra.events.messaging.config;

import com.bcra.events.dlq.DeadLetterPublisher;
import com.bcra.events.messaging.schema.InvalidEventException;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.listener.MessageListenerContainer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class KafkaMessagingConfigurationTest {
    private final KafkaMessagingConfiguration configuration = new KafkaMessagingConfiguration();

    @Test
    @DisplayName("Errores de validación van a DLQ en el primer intento")
    void invalidEventIsNotRetried() {
        var publisher = mock(DeadLetterPublisher.class);
        var handler = configuration.kafkaErrorHandler(publisher, 3, 1, 4);
        var record = new ConsumerRecord<>("wa.message.received.v1", 0, 1, "conversation", "{}");
        var exception = new InvalidEventException();
        assertThat(handler.handleOne(exception, record, null, mock(MessageListenerContainer.class))).isTrue();
        verify(publisher).accept(eq(record), any(), eq(exception));
    }

    @Test
    @DisplayName("Reintentos transitorios son acotados y terminan en DLQ")
    void transientFailureExhaustsConfiguredRetries() {
        var publisher = mock(DeadLetterPublisher.class);
        var handler = configuration.kafkaErrorHandler(publisher, 2, 1, 2);
        var record = new ConsumerRecord<>("wa.message.received.v1", 0, 1, "conversation", "{}");
        var exception = new IllegalStateException("synthetic failure");
        var container = mock(MessageListenerContainer.class);
        assertThat(handler.handleOne(exception, record, null, container)).isFalse();
        assertThat(handler.handleOne(exception, record, null, container)).isFalse();
        verifyNoInteractions(publisher);
        assertThat(handler.handleOne(exception, record, null, container)).isTrue();
        verify(publisher, times(1)).accept(eq(record), any(), eq(exception));
    }

    @Test
    @DisplayName("Rechaza configuración de reintentos inválida")
    void rejectsInvalidRetrySettings() {
        var publisher = mock(DeadLetterPublisher.class);
        assertThatThrownBy(() -> configuration.kafkaErrorHandler(publisher, -1, 1, 2))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> configuration.kafkaErrorHandler(publisher, 1, 0, 2))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> configuration.kafkaErrorHandler(publisher, 1, 2, 1))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
