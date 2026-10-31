package com.bcra.gateway.observability;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WebhookMetricsFilterTest {
    @Test
    void recordsBoundedMetricsAndSafeCorrelatedLogsForSuccessAndRejections() throws Exception {
        var registry = new SimpleMeterRegistry();
        var filter = new WebhookMetricsFilter(registry);
        var logger = (Logger) LoggerFactory.getLogger(WebhookMetricsFilter.class);
        var logs = new ListAppender<ILoggingEvent>();
        logs.start();
        logger.addAppender(logs);
        try {
            for (String path : new String[] {"/webhook/twilio", "/webhooks/twilio/status"}) {
                for (int status : new int[] {200, 403, 413, 429, 503}) {
                    var request = new MockHttpServletRequest("POST", path);
                    request.setContent("Body=private&From=whatsapp:+15005550006".getBytes());
                    request.addHeader("X-Twilio-Signature", "private-signature");
                    var response = new MockHttpServletResponse();
                    new CorrelationIdFilter().doFilter(request, response, (req, res) ->
                            filter.doFilter(req, res, (innerReq, innerRes) -> response.setStatus(status)));
                    String endpoint = path.equals("/webhook/twilio") ? "inbound" : "status";
                    assertThat(registry.get("gateway.webhook.requests").tags("endpoint", endpoint,
                            "status", Integer.toString(status)).counter().count()).isEqualTo(1);
                    assertThat(registry.get("gateway.webhook.duration").tags("endpoint", endpoint,
                            "status", Integer.toString(status)).timer().count()).isEqualTo(1);
                    var event = logs.list.getLast();
                    assertThat(event.getMDCPropertyMap().get("correlationId"))
                            .isEqualTo(response.getHeader("correlationId"));
                    assertThat(event.getFormattedMessage()).doesNotContain("private", "15005550006");
                }
            }
            assertThat(MDC.get("correlationId")).isNull();
        } finally {
            logger.detachAppender(logs);
            logs.stop();
        }
    }

    @Test
    void countsUnexpectedFailuresAndSkipsOtherPaths() throws Exception {
        var registry = new SimpleMeterRegistry();
        var filter = new WebhookMetricsFilter(registry);
        assertThatThrownBy(() -> filter.doFilter(new MockHttpServletRequest("POST", "/webhook/twilio"),
                new MockHttpServletResponse(), (req, res) -> { throw new IOException("private"); }))
                .isInstanceOf(IOException.class);
        assertThat(registry.get("gateway.webhook.requests").tag("status", "500").counter().count()).isEqualTo(1);
        filter.doFilter(new MockHttpServletRequest("GET", "/actuator/health"),
                new MockHttpServletResponse(), (req, res) -> { });
        assertThat(registry.getMeters()).hasSize(2);
    }
}
