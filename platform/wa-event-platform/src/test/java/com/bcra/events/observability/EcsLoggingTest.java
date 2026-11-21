package com.bcra.events.observability;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.OutputStreamAppender;
import com.bcra.events.domain.EventEnvelope;
import com.bcra.events.domain.EventType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.boot.logging.logback.StructuredLogEncoder;
import org.springframework.core.env.Environment;
import org.springframework.mock.env.MockEnvironment;

/**
 * Comprueba la salida real de logs estructurados: el codificador ECS de Spring Boot (el mismo que usa la consola
 * según {@code application.yml}) emite JSON con los campos que {@link CorrelationMdc} pone en el MDC, y ningún
 * teléfono viaja sin enmascarar.
 */
class EcsLoggingTest {
    private static final Pattern UNMASKED_PHONE = Pattern.compile("\\+[0-9]{6,}");
    private static final String LOGGER_NAME = "com.bcra.events.ecs-logging-test";

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void writesEcsJsonWithMdcFieldsAndOnlyMaskedPhones() throws Exception {
        EventEnvelope event = event();
        String phone = "+5491122334455";

        String output = capture(logger -> {
            try (CorrelationMdc ignored = new CorrelationMdc(event)) {
                logger.info("inbound message from {}", PhoneMasker.mask(phone));
            }
        });

        JsonNode line = singleLine(output);
        assertThat(line.path("ecs").path("version").asText()).isEqualTo("8.11");
        assertThat(line.path("log").path("level").asText()).isEqualTo("INFO");
        assertThat(line.path("log").path("logger").asText()).isEqualTo(LOGGER_NAME);
        assertThat(line.has("@timestamp")).isTrue();

        assertThat(line.path("correlationId").asText()).isEqualTo(event.correlationId());
        assertThat(line.path("conversationId").asText()).isEqualTo(event.conversationId());
        assertThat(line.path("messageId").asText()).isEqualTo(event.messageId());
        assertThat(line.path("eventType").asText()).isEqualTo("MessageReceived");

        assertThat(line.path("message").asText()).isEqualTo("inbound message from +549*********55");
        assertThat(UNMASKED_PHONE.matcher(output).results()).as("teléfonos sin enmascarar en el log").isEmpty();
    }

    @Test
    void writesNoCorrelationFieldsOnceTheScopeIsClosed() throws Exception {
        EventEnvelope event = event();
        try (CorrelationMdc ignored = new CorrelationMdc(event)) {
            assertThat(MDC.get("correlationId")).isEqualTo(event.correlationId());
        }
        assertThat(MDC.get("correlationId")).isNull();
        assertThat(MDC.get("conversationId")).isNull();
        assertThat(MDC.get("messageId")).isNull();
        assertThat(MDC.get("eventType")).isNull();

        JsonNode line = singleLine(capture(logger -> logger.info("outside any event")));
        assertThat(line.has("correlationId")).isFalse();
        assertThat(line.has("conversationId")).isFalse();
        assertThat(line.has("messageId")).isFalse();
        assertThat(line.has("eventType")).isFalse();
    }

    @Test
    void consoleIsConfiguredAsEcsForEveryEnvironment() throws Exception {
        assertThat(Files.readString(Path.of("src/main/resources/application.yml"))).contains("console: ecs");
    }

    private EventEnvelope event() {
        return new EventEnvelope(UUID.randomUUID(), EventType.MessageReceived, 1, Instant.now(),
            UUID.randomUUID().toString(), "conv-7d2f4a90-1c3b-5e6f-8a7b-9c0d1e2f3a4b", "SM-ecs-1",
            mapper.createObjectNode());
    }

    /** Escribe con el codificador ECS real de Spring Boot sobre el MDC real y devuelve lo emitido. */
    private String capture(java.util.function.Consumer<Logger> logging) {
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        Object previousEnvironment = context.getObject(Environment.class.getName());
        if (previousEnvironment == null) {
            context.putObject(Environment.class.getName(),
                new MockEnvironment().withProperty("spring.application.name", "wa-event-platform"));
        }

        StructuredLogEncoder encoder = new StructuredLogEncoder();
        encoder.setContext(context);
        encoder.setFormat("ecs");
        encoder.setCharset(StandardCharsets.UTF_8);
        encoder.start();

        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        OutputStreamAppender<ILoggingEvent> appender = new OutputStreamAppender<>();
        appender.setContext(context);
        appender.setEncoder(encoder);
        appender.setOutputStream(buffer);
        appender.start();

        Logger logger = context.getLogger(LOGGER_NAME);
        logger.setLevel(Level.INFO);
        logger.addAppender(appender);
        try {
            logging.accept(logger);
        } finally {
            logger.setLevel(null);
            logger.detachAppender(appender);
            appender.stop();
            encoder.stop();
            if (previousEnvironment == null) {
                context.removeObject(Environment.class.getName());
            }
        }
        return buffer.toString(StandardCharsets.UTF_8);
    }

    private JsonNode singleLine(String output) throws Exception {
        List<String> lines = output.lines().filter(line -> !line.isBlank()).toList();
        assertThat(lines).as("líneas emitidas por el codificador ECS").hasSize(1);
        return mapper.readTree(lines.getFirst());
    }
}
