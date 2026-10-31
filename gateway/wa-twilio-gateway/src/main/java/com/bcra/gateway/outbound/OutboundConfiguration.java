package com.bcra.gateway.outbound;

import java.io.IOException;
import java.net.URI;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries;

import com.bcra.gateway.messaging.producer.StatusEventPublisher;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.twilio.http.TwilioRestClient;

import io.micrometer.core.instrument.MeterRegistry;

@Configuration
@EnableKafka
@ConditionalOnProperty(name = "gateway.outbound.enabled", havingValue = "true", matchIfMissing = true)
public class OutboundConfiguration {
    @Bean
    OutboundSchemas outboundSchemas(ObjectMapper mapper) throws IOException {
        return new OutboundSchemas(mapper);
    }

    @Bean
    OutboundStore outboundStore(JdbcTemplate jdbc) {
        return new OutboundStore(jdbc);
    }

    @Bean
    TwilioSender twilioSender(@Value("${TWILIO_ACCOUNT_SID}") String account,
            @Value("${TWILIO_AUTH_TOKEN}") String token, @Value("${TWILIO_WHATSAPP_FROM}") String from,
            @Value("${TWILIO_STATUS_CALLBACK_PUBLIC_URL}") URI callback) {
        var client = new TwilioRestClient.Builder(account, token).httpClient(new SingleAttemptHttpClient()).build();
        return new TwilioSender(client, from, callback);
    }

    @Bean
    OutboundEventConsumer outboundConsumer(OutboundSchemas schemas, OutboundStore store, TwilioSender sender,
            StatusEventPublisher publisher, ObjectMapper mapper, MeterRegistry metrics) {
        return new OutboundEventConsumer(schemas, store, sender, publisher, mapper, metrics);
    }

    @Bean
    OutboundDeadLetterPublisher outboundDeadLetterPublisher(KafkaTemplate<String, String> kafka,
            ObjectMapper mapper, OutboundSchemas schemas, MeterRegistry metrics) {
        return new OutboundDeadLetterPublisher(kafka, mapper, schemas, metrics);
    }

    @Bean
    ConcurrentKafkaListenerContainerFactory<String, String> outboundKafkaListenerContainerFactory(
            ConsumerFactory<String, String> consumerFactory, OutboundDeadLetterPublisher publisher) {
        var backOff = new ExponentialBackOffWithMaxRetries(3);
        backOff.setInitialInterval(1000);
        backOff.setMultiplier(2.0);
        backOff.setMaxInterval(10000);
        var handler = new DefaultErrorHandler(publisher, backOff);
        handler.setClassifications(java.util.Map.of(OutboundException.class, true), true);
        handler.setBackOffFunction((record, exception) -> {
            Throwable cause = exception;
            while (cause.getCause() != null && !(cause instanceof OutboundException)) {
                cause = cause.getCause();
            }
            return cause instanceof OutboundException failure && failure.kind() != OutboundException.Kind.TRANSIENT
                    ? new org.springframework.util.backoff.FixedBackOff(0, 0) : backOff;
        });
        handler.setCommitRecovered(true);
        // Kafka's default error-handler logging includes the original record; suppress payloads and SDK causes.
        handler.setLogLevel(org.springframework.kafka.KafkaException.Level.DEBUG);
        var factory = new ConcurrentKafkaListenerContainerFactory<String, String>();
        factory.setConsumerFactory(consumerFactory);
        factory.setConcurrency(1);
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);
        factory.setCommonErrorHandler(handler);
        return factory;
    }
}
