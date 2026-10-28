package com.bcra.orchestrator.messaging.consumer;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@SpringBootTest(properties = "spring.kafka.listener.auto-startup=false")
@ActiveProfiles("test")
class InboundTransactionTest {
    final InboundEventConsumer consumer;
    final JdbcTemplate jdbc;

    @Autowired
    InboundTransactionTest(InboundEventConsumer consumer, JdbcTemplate jdbc) {
        this.consumer = consumer;
        this.jdbc = jdbc;
    }

    @Test
    @DisplayName("Confirma el commit antes del ACK y no repite el efecto ante una reentrega")
    void commitsBeforeAckAndDoesNotRepeatEffectOnRedelivery() {
        String message = UUID.randomUUID().toString();
        String conversation = UUID.randomUUID().toString();
        var record = InboundEventConsumerTest.record(message, conversation);
        Runnable assertion = () -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            assertThat(count("message_dedupe", message)).isEqualTo(1);
            assertThat(count("inbound_test_effect", message)).isEqualTo(1);
        };
        consumer.consume(record, assertion::run);
        consumer.consume(InboundEventConsumerTest.record(message, "other-conversation"), assertion::run);
    }

    @Test
    @DisplayName("Revierte deduplicación y efecto fallido y permite reintentar")
    void rollsBackDedupeAndEffectThenAllowsRetry() {
        String message = "fail-" + UUID.randomUUID();
        var record = InboundEventConsumerTest.record(message, "rollback-conversation");
        assertThatThrownBy(() -> consumer.consume(record, () -> {
            throw new AssertionError("Must not acknowledge rollback");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(count("message_dedupe", message)).isZero();
        assertThat(count("inbound_test_effect", message)).isZero();
        consumer.consume(InboundEventConsumerTest.record(message, "retry-conversation"), () -> { });
        assertThat(count("inbound_test_effect", message)).isEqualTo(1);
    }

    @Test
    @DisplayName("Acepta identificadores de la longitud máxima del contrato")
    void acceptsContractMaximumIdentifierLengths() {
        String message = UUID.randomUUID() + "m".repeat(220);
        consumer.consume(InboundEventConsumerTest.record(message, "c".repeat(256)), () -> { });
        assertThat(count("message_dedupe", message)).isEqualTo(1);
    }

    @Test
    @DisplayName("Una falla de ACK tras el commit permite reentrega sin duplicar el efecto")
    void acknowledgmentFailureAfterCommitDoesNotRepeatEffect() {
        String message = UUID.randomUUID().toString();
        var record = InboundEventConsumerTest.record(message, "ack-failure-conversation");
        assertThatThrownBy(() -> consumer.consume(record, () -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            throw new IllegalStateException("Synthetic acknowledgment failure");
        })).isInstanceOf(IllegalStateException.class)
                .hasMessage("Synthetic acknowledgment failure");
        assertThat(count("message_dedupe", message)).isEqualTo(1);
        assertThat(count("inbound_test_effect", message)).isEqualTo(1);

        var acknowledgment = mock(Acknowledgment.class);
        consumer.consume(record, acknowledgment);

        verify(acknowledgment).acknowledge();
        assertThat(count("message_dedupe", message)).isEqualTo(1);
        assertThat(count("inbound_test_effect", message)).isEqualTo(1);
    }

    private int count(String table, String message) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE message_id = ?", Integer.class, message);
    }

    @TestConfiguration
    static class Effects {
        @Bean
        EffectProbe effectProbe(JdbcTemplate jdbc) {
            jdbc.execute("CREATE TABLE IF NOT EXISTS inbound_test_effect "
                    + "(effect_order BIGINT GENERATED ALWAYS AS IDENTITY, message_id VARCHAR(256))");
            return new EffectProbe(jdbc);
        }
    }

    record EffectProbe(JdbcTemplate jdbc) {
        @EventListener
        public void process(InboundMessage message) {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
            jdbc.update("INSERT INTO inbound_test_effect (message_id) VALUES (?)", message.messageId());
            if (message.conversationId().equals("rollback-conversation")) {
                throw new IllegalStateException("Synthetic effect failure");
            }
        }
    }
}
