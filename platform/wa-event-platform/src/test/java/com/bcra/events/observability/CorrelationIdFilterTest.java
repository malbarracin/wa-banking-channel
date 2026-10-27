package com.bcra.events.observability;

import jakarta.servlet.ServletException;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CorrelationIdFilterTest {
    @Test
    void logsCorrelationWithoutRequestData() throws Exception {
        var logger = (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(CorrelationIdFilter.class);
        var appender = new ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent>();
        var previousLevel = logger.getLevel();
        appender.start();
        logger.addAppender(appender);
        logger.setLevel(ch.qos.logback.classic.Level.DEBUG);
        var request = new MockHttpServletRequest();
        request.addHeader("Authorization", "synthetic-secret");
        request.setQueryString("phone=synthetic-private");
        var response = new MockHttpServletResponse();
        try {
            new CorrelationIdFilter().doFilter(request, response, (incoming, outgoing) -> { });
            assertThat(appender.list).hasSize(1);
            var event = appender.list.getFirst();
            assertThat(event.getMDCPropertyMap()).containsEntry("correlationId", response.getHeader("correlationId"));
            assertThat(event.getFormattedMessage()).isEqualTo("HTTP request correlation established");
            assertThat(event.getFormattedMessage()).doesNotContain("synthetic-secret", "synthetic-private");
        } finally {
            logger.detachAppender(appender);
            logger.setLevel(previousLevel);
            appender.stop();
        }
    }
    @Test
    void propagatesValidHeaderAndRestoresMdcOnFailure() {
        String correlationId = UUID.randomUUID().toString();
        var request = new MockHttpServletRequest();
        request.addHeader("correlationId", correlationId);
        var response = new MockHttpServletResponse();
        MDC.put("correlationId", "previous");
        try {
            assertThatThrownBy(() -> new CorrelationIdFilter().doFilter(request, response, (incoming, outgoing) -> {
                assertThat(MDC.get("correlationId")).isEqualTo(correlationId);
                throw new ServletException("synthetic");
            })).isInstanceOf(ServletException.class);
            assertThat(response.getHeader("correlationId")).isEqualTo(correlationId);
            assertThat(MDC.get("correlationId")).isEqualTo("previous");
        } finally {
            MDC.clear();
        }
    }

    @Test
    void generatesUuidForMissingOrUnsafeHeadersAndClearsMdc() throws Exception {
        for (String candidate : new String[] {"", "private-value\r\nforged", "invalid"}) {
            var request = new MockHttpServletRequest();
            request.addHeader("correlationId", candidate);
            var response = new MockHttpServletResponse();
            new CorrelationIdFilter().doFilter(request, response, (incoming, outgoing) ->
                    assertThat(MDC.get("correlationId")).isEqualTo(response.getHeader("correlationId")));
            assertThat(UUID.fromString(response.getHeader("correlationId"))).isNotNull();
            assertThat(MDC.get("correlationId")).isNull();
        }
        var response = new MockHttpServletResponse();
        new CorrelationIdFilter().doFilter(new MockHttpServletRequest(), response, (incoming, outgoing) -> { });
        assertThat(UUID.fromString(response.getHeader("correlationId"))).isNotNull();
    }
}
