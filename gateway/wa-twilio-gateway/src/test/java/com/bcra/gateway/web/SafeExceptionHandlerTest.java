package com.bcra.gateway.web;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.bcra.gateway.observability.CorrelationIdFilter;
import com.bcra.gateway.observability.WebhookMetricsFilter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.assertj.core.api.Assertions.assertThat;

class SafeExceptionHandlerTest {
    @RestController
    static class FailingController {
        @GetMapping("/failure")
        public void fail() {
            throw new IllegalStateException("private-token whatsapp:+15005550006");
        }

        @GetMapping("/webhook/twilio")
        public void webhookFailure() {
            fail();
        }
    }

    @Test
    void unexpectedFailureReturnsEmpty500() throws Exception {
        MockMvcBuilders.standaloneSetup(new FailingController()).setControllerAdvice(new SafeExceptionHandler())
                .build().perform(get("/failure?trace=true&message=true"))
                .andExpect(status().isInternalServerError()).andExpect(content().string(""));
    }

    @Test
    void unexpectedWebhookFailureKeepsCorrelationAndMetricsWithoutLoggingSensitiveException() throws Exception {
        var logger = (Logger) LoggerFactory.getLogger(SafeExceptionHandler.class);
        var logs = new ListAppender<ILoggingEvent>();
        logs.start();
        logger.addAppender(logs);
        var registry = new SimpleMeterRegistry();
        try {
            var response = MockMvcBuilders.standaloneSetup(new FailingController())
                    .setControllerAdvice(new SafeExceptionHandler())
                    .addFilters(new CorrelationIdFilter(), new WebhookMetricsFilter(registry))
                    .build().perform(get("/webhook/twilio?trace=true&message=true"))
                    .andExpect(status().isInternalServerError()).andExpect(content().string(""))
                    .andReturn().getResponse();
            assertThat(logs.list).hasSize(1);
            var event = logs.list.getFirst();
            assertThat(event.getFormattedMessage()).doesNotContain("private-token", "15005550006");
            assertThat(event.getThrowableProxy()).isNull();
            assertThat(event.getMDCPropertyMap().get("correlationId"))
                    .isNotBlank().isEqualTo(response.getHeader("correlationId"));
            assertThat(registry.get("gateway.webhook.requests").tags("endpoint", "inbound", "status", "500")
                    .counter().count()).isEqualTo(1);
            assertThat(registry.get("gateway.webhook.duration").tags("endpoint", "inbound", "status", "500")
                    .timer().count()).isEqualTo(1);
            assertThat(MDC.get("correlationId")).isNull();
        } finally {
            registry.close();
            logger.detachAppender(logs);
            logs.stop();
        }
    }
}
