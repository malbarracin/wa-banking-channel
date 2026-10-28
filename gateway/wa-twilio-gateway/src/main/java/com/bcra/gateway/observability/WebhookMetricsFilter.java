package com.bcra.gateway.observability;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(2)
public class WebhookMetricsFilter extends OncePerRequestFilter {
    private static final Logger LOGGER = LoggerFactory.getLogger(WebhookMetricsFilter.class);
    private final MeterRegistry registry;

    public WebhookMetricsFilter(MeterRegistry registry) {
        this.registry = registry;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().equals("/webhook/twilio")
                && !request.getRequestURI().equals("/webhooks/twilio/status");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Timer.Sample sample = Timer.start(registry);
        String endpoint = request.getRequestURI().equals("/webhook/twilio") ? "inbound" : "status";
        int status = 500;
        try {
            chain.doFilter(request, response);
            status = response.getStatus();
        } finally {
            String result = Integer.toString(status);
            sample.stop(registry.timer("gateway.webhook.duration", "endpoint", endpoint, "status", result));
            registry.counter("gateway.webhook.requests", "endpoint", endpoint, "status", result).increment();
            LOGGER.info("Webhook completed endpoint={} status={}", endpoint, result);
        }
    }
}
